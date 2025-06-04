import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class Main extends Application {
    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Pack Updater");

        Button checkUpdatesButton = new Button("Check for Updates");
        checkUpdatesButton.setOnAction(e -> {
            System.out.println("Update check clicked!");
        });

        VBox layout = new VBox(10);
        layout.getChildren().addAll(checkUpdatesButton);

        Scene scene = new Scene(layout, 300, 200);
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
