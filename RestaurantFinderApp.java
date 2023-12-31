import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.geometry.Pos;
import javafx.scene.paint.Color;
import java.util.prefs.Preferences;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Random;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class RestaurantFinderApp extends Application {
    private Label addressLabel = new Label();
    private HBox leftRestaurantContainer = new HBox();
    private HBox rightRestaurantContainer = new HBox();
    private HBox restaurantsContainer = new HBox(20, leftRestaurantContainer, rightRestaurantContainer);

    private Button refreshButton = new Button("Refresh");
    private Button changeAddressButton = new Button("Change Address");

    private String apiKey = System.getenv("APIKEY");

    private Preferences preferences;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Restaurant Finder");

        VBox root = new VBox(20);
        root.setPadding(new Insets(20));
        root.getChildren().addAll(createHeader(), restaurantsContainer, createFooter());
        root.setStyle("-fx-background-color: #B0E57C;");
        root.setAlignment(Pos.CENTER);

        Scene scene = new Scene(root, 480, 400);
        primaryStage.setScene(scene);

        HBox buttonBox = new HBox(10, refreshButton, changeAddressButton);
        buttonBox.setAlignment(Pos.CENTER);
        root.getChildren().add(buttonBox);

        preferences = Preferences.userNodeForPackage(RestaurantFinderApp.class);
        String savedAddress = preferences.get("address", "452 Harmon Rd, Philadelphia, PA");
        addressLabel.setText("Address: " + savedAddress);

        changeAddressButton.setOnAction(e -> changeAddress());
        refreshButton.setOnAction(e -> findRandomRestaurants());

        findRandomRestaurants();

        primaryStage.show();
    }

    private VBox createHeader() {
        VBox header = new VBox();
        Label titleLabel = new Label("Restaurant Finder");
        titleLabel.setFont(Font.font("Verdana", FontWeight.BOLD, 24));
        header.getChildren().addAll(titleLabel, addressLabel);
        header.setAlignment(Pos.CENTER);
        return header;
    }

    private void changeAddress() {
        TextInputDialog dialog = new TextInputDialog(addressLabel.getText().substring(9));
        dialog.setTitle("Change Address");
        dialog.setHeaderText("Enter a new address:");
        dialog.setContentText("New Address:");

        dialog.showAndWait().ifPresent(newAddress -> {
            if (!newAddress.isEmpty()) {
                addressLabel.setText("Address: " + newAddress);

                preferences.put("address", newAddress);
                findRandomRestaurants();
            }
        });
    }

    private HBox createFooter() {
        HBox footer = new HBox();
        refreshButton.setPrefSize(120, 30);
        footer.getChildren().add(refreshButton);
        footer.setAlignment(Pos.CENTER);
        return footer;
    }

    private void findRandomRestaurants() {
        new Thread(() -> {
            try {
                String encodedAddress = java.net.URLEncoder.encode(addressLabel.getText().substring(9), "UTF-8");
                String apiUrl = "https://maps.googleapis.com/maps/api/place/textsearch/json?query=restaurant+near+" + encodedAddress + "&key=" + apiKey;

                URL url = new URL(apiUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();

                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;

                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }

                reader.close();

                Platform.runLater(() -> handleApiResponse(response.toString()));
            } catch (IOException e) {
                e.printStackTrace();
                Platform.runLater(() -> handleRestaurantDetailsError(leftRestaurantContainer));
                Platform.runLater(() -> handleRestaurantDetailsError(rightRestaurantContainer));
            }
        }).start();
    }

    private void handleApiResponse(String jsonResponse) {
        JsonObject jsonResponseObject = JsonParser.parseString(jsonResponse).getAsJsonObject();
        JsonArray results = jsonResponseObject.getAsJsonArray("results");

        if (results.size() > 1) {
            leftRestaurantContainer.getChildren().clear();
            rightRestaurantContainer.getChildren().clear();

            Random random = new Random();
            int randomIndex1 = random.nextInt(results.size());
            int randomIndex2 = random.nextInt(results.size());

            JsonObject restaurant1 = results.get(randomIndex1).getAsJsonObject();
            JsonObject restaurant2 = results.get(randomIndex2).getAsJsonObject();

            getRestaurantDetails(restaurant1, leftRestaurantContainer);
            getRestaurantDetails(restaurant2, rightRestaurantContainer);
        } else {
            handleRestaurantDetailsError(leftRestaurantContainer);
            handleRestaurantDetailsError(rightRestaurantContainer);
        }
    }

    private void getRestaurantDetails(JsonObject restaurant, HBox restaurantContainer) {
        new Thread(() -> {
            try {
                String placeId = restaurant.get("place_id").getAsString();
                String apiUrl = "https://maps.googleapis.com/maps/api/place/details/json?placeid=" + placeId + "&fields=name,rating,formatted_address,formatted_phone_number&key=" + apiKey;

                URL url = new URL(apiUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();

                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;

                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }

                reader.close();

                Platform.runLater(() -> handleRestaurantDetailsResponse(response.toString(), restaurantContainer));
            } catch (IOException e) {
                e.printStackTrace();
                Platform.runLater(() -> handleRestaurantDetailsError(restaurantContainer));
            }
        }).start();
    }

    private void handleRestaurantDetailsResponse(String jsonResponse, HBox restaurantContainer) {
        JsonObject restaurantDetails = JsonParser.parseString(jsonResponse).getAsJsonObject().getAsJsonObject("result");

        String restaurantName = restaurantDetails.has("name") ? restaurantDetails.get("name").getAsString() : "N/A";
        double rating = restaurantDetails.has("rating") ? restaurantDetails.get("rating").getAsDouble() : 0.0;
        String formattedAddress = restaurantDetails.has("formatted_address") ? restaurantDetails.get("formatted_address").getAsString() : "Address not found";
        String phoneNumber = restaurantDetails.has("formatted_phone_number") ? restaurantDetails.get("formatted_phone_number").getAsString() : "Phone number not found";

        String address = formattedAddress.split(",")[0];

        addRestaurantInfo(restaurantName, rating, phoneNumber, address, restaurantContainer);
    }

    private void handleRestaurantDetailsError(HBox restaurantContainer) {
        addRestaurantInfo("N/A", 0.0, "Phone number not found", "Address not found", restaurantContainer);
    }

    private void addRestaurantInfo(String restaurantName, double rating, String phoneNumber, String address, HBox restaurantContainer) {
        VBox restaurantInfo = new VBox(10);
        Label nameLabel = new Label("Name: ");
        nameLabel.setFont(Font.font("Verdana", FontWeight.BOLD, 14));
        Label nameValueLabel = new Label(restaurantName);
        nameValueLabel.setFont(Font.font("Verdana", FontWeight.NORMAL, 14));
        Label ratingLabel = new Label("Rating: ");
        ratingLabel.setFont(Font.font("Verdana", FontWeight.BOLD, 14));
        Label ratingValueLabel = new Label(String.valueOf(rating));
        ratingValueLabel.setFont(Font.font("Verdana", FontWeight.NORMAL, 14));
        Label addressLabel = new Label("Address: ");
        addressLabel.setFont(Font.font("Verdana", FontWeight.BOLD, 14));
        Label addressValueLabel = new Label(address);
        addressValueLabel.setFont(Font.font("Verdana", FontWeight.NORMAL, 14));
        Label phoneLabel = new Label("Phone: ");
        phoneLabel.setFont(Font.font("Verdana", FontWeight.BOLD, 14));
        Label phoneValueLabel = new Label(phoneNumber);
        phoneValueLabel.setFont(Font.font("Verdana", FontWeight.NORMAL, 14));

        restaurantInfo.getChildren().addAll(
            nameLabel, nameValueLabel,
            ratingLabel, ratingValueLabel,
            addressLabel, addressValueLabel,
            phoneLabel, phoneValueLabel
        );

        Label moreInfoLabel = new Label("More Info...");
        moreInfoLabel.setFont(Font.font("Verdana", FontWeight.BOLD, 14));
        Button moreInfoButton = new Button("", moreInfoLabel);
        moreInfoButton.setOnAction(e -> openGoogleMapsLink(restaurantName));
        restaurantInfo.getChildren().add(moreInfoButton);

        restaurantContainer.getChildren().add(restaurantInfo);
    }

    private void openGoogleMapsLink(String placeName) {
        String googleMapsLink = "https://www.google.com/maps/place/?q=" + placeName;
        getHostServices().showDocument(googleMapsLink);
    }

}