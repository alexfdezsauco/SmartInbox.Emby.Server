package smartinbox.emby.controllers;

import org.apache.commons.io.FileUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import smartinbox.emby.models.Recommendation;
import smartinbox.emby.services.Trainer;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

@RestController()
@RequestMapping("/api/smartinbox")
public class SmartInboxController {

    private final Trainer trainer;

    public SmartInboxController(Trainer trainer){
        this.trainer = trainer;
    }

    @RequestMapping("/greeting")
    public String greeting() {
        return "Hello";
    }

    @PostMapping(value = "/train", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> train(@RequestParam(name = "maxEpochs", required = false, defaultValue = "100") int maxEpochs,
                                   @RequestParam(name = "maxEpochsWithNoImprovement", required = false, defaultValue = "10") int maxEpochsWithNoImprovement,
                                   @RequestParam(name = "newMoviesCount", required = false, defaultValue = "50") int newMoviesCount,
                                   @RequestParam(name = "oldMoviesToTreatAsNew", required = false, defaultValue = "5") int oldMoviesToTreatAsNew,
                                   @RequestPart("file") MultipartFile file) {
        UUID trainingId = UUID.randomUUID();
        try {
            Path path = Paths.get(".data/" + trainingId.toString());
            if(!Files.exists(path)){
                Files.createDirectories(path);
            }

            File database = new File(path.toFile(),trainingId + ".db");
            FileUtils.writeByteArrayToFile(database, file.getBytes());
            this.trainer.trainAsync(trainingId, maxEpochs, maxEpochsWithNoImprovement, newMoviesCount, oldMoviesToTreatAsNew);

        } catch (IOException e) {
            e.printStackTrace();
            return new ResponseEntity<String>("Failed", HttpStatus.INTERNAL_SERVER_ERROR);
        }

        return new ResponseEntity<UUID>(trainingId, HttpStatus.OK);
    }

    @GetMapping(value = "/recommendations", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getRecommendations(@RequestParam("id") UUID id) throws IOException, InterruptedException {
//        if(!this.trainer.contains(id)){
//            return new ResponseEntity(String.format("There is no training with id '%s'", id), HttpStatus.BAD_REQUEST);
//        }

        if( this.trainer.isRunning(id)){
            return new ResponseEntity<String>(String.format("The training with id '%s' is already running", id), HttpStatus.BAD_REQUEST);
        }

        return new ResponseEntity<List<Recommendation>>(this.trainer.getRecommendations(id), HttpStatus.OK);
    }
}
