package smartinbox.emby.services;

import com.opencsv.CSVWriter;
import org.datavec.api.records.reader.impl.csv.CSVRecordReader;
import org.datavec.api.records.reader.impl.transform.TransformProcessRecordReader;
import org.datavec.api.split.FileSplit;
import org.datavec.api.transform.TransformProcess;
import org.datavec.api.transform.analysis.DataAnalysis;
import org.datavec.api.transform.schema.Schema;
import org.datavec.api.transform.transform.normalize.Normalize;
import org.datavec.api.writable.Writable;
import org.datavec.local.transforms.AnalyzeLocal;
import org.deeplearning4j.api.storage.StatsStorage;
import org.deeplearning4j.datasets.datavec.RecordReaderDataSetIterator;
import org.deeplearning4j.earlystopping.EarlyStoppingConfiguration;
import org.deeplearning4j.earlystopping.EarlyStoppingResult;
import org.deeplearning4j.earlystopping.saver.LocalFileModelSaver;
import org.deeplearning4j.earlystopping.scorecalc.DataSetLossCalculator;
import org.deeplearning4j.earlystopping.termination.MaxEpochsTerminationCondition;
import org.deeplearning4j.earlystopping.termination.ScoreImprovementEpochTerminationCondition;
import org.deeplearning4j.earlystopping.trainer.EarlyStoppingTrainer;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.inputs.InputType;
import org.deeplearning4j.nn.conf.layers.DenseLayer;
import org.deeplearning4j.nn.conf.layers.OutputLayer;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.nn.weights.WeightInit;
import org.deeplearning4j.ui.stats.StatsListener;
import org.deeplearning4j.ui.storage.InMemoryStatsStorage;
import org.deeplearning4j.util.ModelSerializer;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.api.DataSet;
import org.nd4j.linalg.learning.config.Adam;
import org.nd4j.linalg.lossfunctions.impl.LossHinge;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import smartinbox.emby.models.Recommendation;
import smartinbox.emby.models.RecommendationType;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class Trainer {
    private HashMap<UUID, Boolean> isRunning = new HashMap<UUID, Boolean>();

    private synchronized void complete(UUID trainingId){
        this.isRunning.put(trainingId, false);
    }

    @Async
    public synchronized void trainAsync(UUID trainingId) {
        if(!isRunning.containsKey(trainingId)){
            isRunning.put(trainingId, false);
        }

        if (!isRunning.get(trainingId)) {
            isRunning.put(trainingId, true);

            try {
                this.train(trainingId);
            } catch (SQLException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            finally {
                this.complete(trainingId);
            }
        }
    }

    private void train(UUID uuid) throws SQLException, IOException, InterruptedException {
        String trainingId = uuid.toString();

        Path dataDirectory = Paths.get(".data/" + uuid.toString());
        File database = new File(dataDirectory.toFile(),uuid + ".db");
        String url = "jdbc:sqlite:"+ database.getPath();
        Connection connection = DriverManager.getConnection(url);
        Statement statement = connection.createStatement();

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime createDateForNewMovies = now.minus(1, ChronoUnit.MONTHS);

        int newMoviesCount = 50;
        ResultSet countResultSet = statement.executeQuery(String.format("SELECT COUNT(*) FROM Movies WHERE Id NOT IN (SELECT Id FROM Movies WHERE IsDeleted = false ORDER BY DateCreated DESC LIMIT %d)", newMoviesCount));
        countResultSet.next();
        int count = countResultSet.getInt(1);
        int trainingSetSize = (int) (count * 0.80);
        int evaluationSetSize = count - trainingSetSize;

        String groundTruthBaseQuery = String.format("SELECT * FROM Movies WHERE Id NOT IN (SELECT Id FROM Movies WHERE IsDeleted = false ORDER BY DateCreated DESC LIMIT %d)", newMoviesCount);
        ResultSet resultSet = statement.executeQuery(groundTruthBaseQuery + " LIMIT 1");
        Schema.Builder builder = new Schema.Builder();
        ResultSetMetaData metaData = resultSet.getMetaData();
        int columnCount = metaData.getColumnCount();
        builder.addColumnString("Id");
        builder.addColumnString("Name");
        for (int i = 1; i <= columnCount; i++){
            String columnName = metaData.getColumnName(i);
            if(metaData.getColumnType(i) == 6){
                builder.addColumnDouble(columnName);
            }
            else if(metaData.getColumnType(i) == 4){
                builder.addColumnCategorical(columnName, "0", "1");
            }
        }

        Schema schema = builder.build();
        BufferedWriter schemaBufferedWriter = Files.newBufferedWriter(Paths.get(dataDirectory.toString(),trainingId + "-schema.json"));
        schemaBufferedWriter.write(schema.toJson());
        schemaBufferedWriter.flush();
        schemaBufferedWriter.close();

        String selectedColumnsCS = "";
        List<String> columnNames = schema.getColumnNames();
        for (int i = 0; i < schema.numColumns(); i++){
            selectedColumnsCS += ", " + columnNames.get(i);
        }

        selectedColumnsCS = selectedColumnsCS.substring(1).trim();
        String selectedColumnQuery = groundTruthBaseQuery.replace("*", selectedColumnsCS);
        ResultSet trainingSet = statement.executeQuery(selectedColumnQuery + " LIMIT " + trainingSetSize);
        File trainingFile = new File(dataDirectory.toFile(), trainingId + "-traning.csv");
        CSVWriter trainingSetWriter = new CSVWriter(new FileWriter(trainingFile));
        trainingSetWriter.writeAll(trainingSet, true);
        trainingSetWriter.close();

        ResultSet evaluationSet = statement.executeQuery(selectedColumnQuery + " LIMIT " + evaluationSetSize + " OFFSET " + trainingSetSize);
        File evaluationFile = new File(dataDirectory.toFile(), trainingId + "-evaluation.csv");
        CSVWriter evaluationSetWriter = new CSVWriter(new FileWriter(evaluationFile));
        evaluationSetWriter.writeAll(evaluationSet, true);
        evaluationSetWriter.close();

        String newBaseQuery = String.format("SELECT * FROM Movies WHERE IsDeleted = false ORDER BY DateCreated DESC LIMIT %d", newMoviesCount).replace("*", selectedColumnsCS);
        ResultSet newSet = statement.executeQuery(newBaseQuery);
        File newFile = new File(dataDirectory.toFile(), trainingId + "-new.csv");
        CSVWriter newSetWriter = new CSVWriter(new FileWriter(newFile));
        newSetWriter.writeAll(newSet, true);
        newSetWriter.close();

        FileSplit trainingFileSplit = new FileSplit(trainingFile);
        CSVRecordReader rawRecordReader = new CSVRecordReader(1, ',');
        rawRecordReader.initialize(trainingFileSplit);

        DataAnalysis analysis = AnalyzeLocal.analyze(schema, rawRecordReader);
        BufferedWriter bufferedWriter = Files.newBufferedWriter(Paths.get(dataDirectory.toString(),trainingId + "-analysis.json"));
        bufferedWriter.write(analysis.toJson());
        bufferedWriter.flush();
        bufferedWriter.close();

        TransformProcess transformProcess = getTransformProcess(schema, analysis);

        Schema finalSchema = transformProcess.getFinalSchema();
        TransformProcessRecordReader trainingRecordReader = new TransformProcessRecordReader(new CSVRecordReader(1, ','), transformProcess);
        trainingRecordReader.initialize(trainingFileSplit);

        int batchSize = 80;
        RecordReaderDataSetIterator trainingDataSetIterator = new RecordReaderDataSetIterator.Builder(trainingRecordReader, batchSize)
                .classification(finalSchema.getIndexOfColumn("IsPlayed"), 2)
                .build();

        MultiLayerConfiguration config = new NeuralNetConfiguration.Builder()
                .seed(0xC0FFEE)
                .weightInit(WeightInit.XAVIER)
                // .activation(Activation.TANH)
                .activation(Activation.RELU)
                .updater(new Adam.Builder().learningRate(0.001).build())
                .l2(0.0000316)
                .list(
                        new DenseLayer.Builder().nOut(25).build(),
                        new DenseLayer.Builder().nOut(25).build(),
                        new DenseLayer.Builder().nOut(25).build(),
                        new DenseLayer.Builder().nOut(25).build(),
                        new DenseLayer.Builder().nOut(25).build(),
                        new OutputLayer.Builder(new LossHinge())
                                .nOut(2).activation(Activation.SOFTMAX).build()
                )
                .setInputType(InputType.feedForward(finalSchema.numColumns() - 1))
                .build();

        //UIServer uiServer = UIServer.getInstance();
        StatsStorage statsStorage = new InMemoryStatsStorage();
        // uiServer.attach(statsStorage);

        MultiLayerNetwork network = new MultiLayerNetwork(config);
        StatsListener statsListener = new StatsListener(statsStorage);
        network.setListeners(statsListener);

        FileSplit evalSplit = new FileSplit(evaluationFile);
        TransformProcessRecordReader evalRecordReader = new TransformProcessRecordReader(new CSVRecordReader(1, ','), transformProcess);
        evalRecordReader.initialize(evalSplit);

        RecordReaderDataSetIterator evaluationDataSetIterator = new RecordReaderDataSetIterator.Builder(evalRecordReader, batchSize)
                .classification(finalSchema.getIndexOfColumn("IsPlayed"), 2)
                .build();

        EarlyStoppingConfiguration.Builder earlyStoppingConfigurationBuilder = new EarlyStoppingConfiguration.Builder();
        Path directoryPath = Paths.get(uuid.toString());
        if(!Files.exists(directoryPath)){
            Files.createDirectory(directoryPath);
        }

        EarlyStoppingConfiguration earlyStoppingConfiguration = earlyStoppingConfigurationBuilder
                .epochTerminationConditions(
                        new ScoreImprovementEpochTerminationCondition(10),
                        new MaxEpochsTerminationCondition(100))
                .scoreCalculator(new DataSetLossCalculator(evaluationDataSetIterator, true))
                .evaluateEveryNEpochs(1)
                .modelSaver(new LocalFileModelSaver(".models/" + trainingId))
                .build();
        EarlyStoppingTrainer trainer = new EarlyStoppingTrainer(earlyStoppingConfiguration, network, trainingDataSetIterator);
        EarlyStoppingResult<MultiLayerNetwork> result = trainer.fit();
    }

    private TransformProcess getTransformProcess(Schema schema, DataAnalysis analysis) {
        TransformProcess.Builder transformBuilder = new TransformProcess.Builder(schema);
        transformBuilder = transformBuilder.removeColumns("Id", "Name", "IsDeleted");
        List<String> columnNames = schema.getColumnNames();
        for (String columnName : columnNames) {
            if(columnName.startsWith("Is") && !columnName.equals("IsPlayed") && !columnName.equals("IsDeleted")){
                transformBuilder = transformBuilder.categoricalToOneHot(columnName);
            }
        }

        return transformBuilder.normalize("CommunityRating", Normalize.Standardize, analysis).build();
    }

    public static void main(String[] args) throws SQLException, IOException, InterruptedException {
        new Trainer().train(UUID.fromString("db987a22-11b4-4e82-9a49-42d3ce923eb1"));
    }

    public List<Recommendation> getRecommendations(UUID trainingId) throws IOException, InterruptedException {

        Path dataDirectory = Paths.get(".data/" + trainingId.toString());

        File newFile = new File(dataDirectory.toFile(), trainingId + "-new.csv");

        DataAnalysis dataAnalysis = DataAnalysis.fromJson(String.join(System.lineSeparator(), Files.readAllLines(Paths.get(dataDirectory.toString(), trainingId + "-analysis.json"))));
        Schema schema = Schema.fromJson(String.join(System.lineSeparator(), Files.readAllLines(Paths.get(dataDirectory.toString(), trainingId + "-schema.json"))));
        TransformProcess transformProcess = getTransformProcess(schema, dataAnalysis);

        CSVRecordReader rawRecordReader = new CSVRecordReader(1, ',');
        TransformProcessRecordReader newTransformProcessRecordReader = new TransformProcessRecordReader(new CSVRecordReader(1, ','), transformProcess);
        Schema finalSchema = transformProcess.getFinalSchema();

        rawRecordReader.initialize(new FileSplit(newFile));
        newTransformProcessRecordReader.initialize(new FileSplit(newFile));

        MultiLayerNetwork multiLayerNetwork = ModelSerializer.restoreMultiLayerNetwork(String.format(".models/%s/bestModel.bin", trainingId));

        int batchSize = 1;
        RecordReaderDataSetIterator newDataSetIterator = new RecordReaderDataSetIterator.Builder(newTransformProcessRecordReader, batchSize)
                .classification(finalSchema.getIndexOfColumn("IsPlayed"), 2)
                .build();


        ArrayList<Recommendation> recommendations = new ArrayList<>();

        while(newDataSetIterator.hasNext()){
            List<Writable> currentRecord = rawRecordReader.next();
            DataSet next = newDataSetIterator.next();
            String id = currentRecord.get(0).toString();
            String title = currentRecord.get(1).toString();

            INDArray features = next.getFeatures();
            int[] predict = multiLayerNetwork.predict(features);
            RecommendationType recommendationType = RecommendationType.values()[predict[0]];
            recommendations.add(new Recommendation(id,  title, recommendationType));
        }

        return recommendations;
    }

    public synchronized boolean isRunning(UUID id) {
        return this.isRunning.containsKey(id) && this.isRunning.get(id);
    }

    public synchronized boolean contains(UUID id) {
        return this.isRunning.containsKey(id) && this.isRunning.get(id);
    }
}
