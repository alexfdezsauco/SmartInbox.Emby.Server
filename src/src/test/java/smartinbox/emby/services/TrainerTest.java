package smartinbox.emby.services;

import org.junit.Test;
import smartinbox.emby.models.Recommendation;
import smartinbox.emby.models.RecommendationType;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

public class TrainerTest {

    @Test
    public void basicTraining() throws InterruptedException, SQLException, IOException {
        Path modelsDirectoryPath = Paths.get("src","test", "resources", "models").toAbsolutePath();
        Path dataDirectoryPath = Paths.get("src","test", "resources", "data").toAbsolutePath();

        File smartInboxFile = Paths.get("src","test", "resources", "smart-inbox.db").toFile().getAbsoluteFile();
        Trainer trainer = new Trainer();
        UUID trainingUniqueIdentifier = UUID.randomUUID();
        trainer.train(trainingUniqueIdentifier, smartInboxFile, dataDirectoryPath, modelsDirectoryPath,100, 100, 100);

        List<Recommendation> recommendations = trainer.getRecommendations(trainingUniqueIdentifier, dataDirectoryPath, modelsDirectoryPath);
        for (Recommendation r: recommendations) {
            if(r.getRecommendationType() == RecommendationType.Play){
                System.out.println(r.getTitle());
            }
        }
    }
}
