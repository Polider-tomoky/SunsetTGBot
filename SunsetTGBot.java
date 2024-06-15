package SunsetTGBot;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.methods.groupadministration.GetChat;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.*;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import java.net.*;

import java.nio.charset.StandardCharsets;

import java.time.*;
import java.time.format.DateTimeFormatter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.sqlite.SQLiteDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class SunsetTGBot extends TelegramLongPollingBot {
    private boolean askingCity = false;
    static final String apiKeyOWM = "8fb093045b16c1c4f3335d2b6911e58d";
    final String apiKeyTZ = "I4IXA6JXPXQ4";
    private static final String BOT_USERNAME = "Закатник";
    private static final String BOT_TOKEN = "7470684275:AAHxMF0qgd_utRb6wvnszgogE3PqWFMRzE0";
    private static DataSource dataSource = null;
    public SunsetTGBot() {
        SQLiteDataSource ds = new SQLiteDataSource();
        ds.setUrl("jdbc:sqlite:userCities.db");
        dataSource = ds;
        initializeDatabase();
    }
    private void saveUserCity(Long chatId, String city) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(
                     "INSERT INTO UserCities(chatId, city) VALUES(?, ?) ON CONFLICT(chatId) DO UPDATE SET city = ?")) {
            pstmt.setLong(1, chatId);
            pstmt.setString(2, city);
            pstmt.setString(3, city);
            pstmt.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }
    private static String getUserCity(Long chatId) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(
                     "SELECT city FROM UserCities WHERE chatId = ?")) {
            pstmt.setLong(1, chatId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("city");
                }
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return null;
    }
    private void initializeDatabase() {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(
                     "CREATE TABLE IF NOT EXISTS UserCities (" +
                             "chatId INTEGER PRIMARY KEY," +
                             "city TEXT NOT NULL)")){
            pstmt.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }
    public void onUpdateReceived(Update update) {
        Message message = update.getMessage();


        if (message != null && message.hasText()) {
            Long chatId = message.getChatId();
            String text = message.getText();
            String city = getUserCity(chatId);

            SendMessage sendMessage = new SendMessage();
            sendMessage.setChatId(chatId);
            sendMessage.enableMarkdown(true);

            if (text.equalsIgnoreCase("/start")) {
                sendMsg(chatId, "Приветствую вас в '*_Закатник_*'! " +
                        "Я здесь, чтобы помочь Вам насладиться закатами и предоставить актуальную погоду. " +
                        "Для получения всей нужной информации: */help*", false, true);
            } else if (text.equalsIgnoreCase("/changecity")) {
                sendMsg(chatId, "Пожалуйста, введите название вашего города:", false, true);
                askingCity = true;
            } else if (text.startsWith("/broadcast") && text.length() > 16  && chatId == 1418333402) {
                String broadcastMessage = text.substring(17);
                broadcastMessage(broadcastMessage);
                sendMsg(chatId, "Сообщение отправлено всем пользователям.", false, true);
            } else if (text.equalsIgnoreCase("/clean") && chatId == 1418333402) {
                clearUserCities();
                sendMsg(chatId, "Очищенно.", false, true);
            } else if (text.equalsIgnoreCase("/stats") && chatId == 1418333402) {
                getStats(chatId);
            } else if (askingCity) {
                if (isValidCity(text)) {
                    askingCity = false;
                    saveUserCity(chatId, text);
                    sendMsg(chatId, "Отлично! Теперь мы будем оповещать Вас о великолепных закатах в '_" + getUserCity(chatId) + "_'", false, true);
                } else if (text.charAt(0) == '/') {
                    sendMsg(chatId, "Пожалуйста, вводите город или поселок, а не команду.", false, true);
                } else {
                    sendMsg(chatId, "Извините, но я не могу найти '_" + text + "_'. Пожалуйста, введите нужный город|поселок на русском языке.", false, true);
                }

            } else if (text.equalsIgnoreCase("/help"))  {
                sendMsg(chatId, "Приветствуем в боте '_Закатник_', я буду вашим проводником в мир танцев света и облаков\n\n" +
                        "Вот мои навыки:\n" +
                        "- */changecity:* _Позволяет Вам выбрать город|поселок, чтобы я мог предоставлять информацию о закатах и погоде именно для Вашего местоположения._\n" +
                        "- */sunset:* _Информирую Вас о сегодняшнем закате в выбранном месте жительсва, чтобы Вы могли насладиться этим волшебным моментом._\n" +
                        "- */weather:* _Предоставляю текущий прогноз погоды, включая температуру, влажность, скорость ветра и другие параметры._\n\n" +
                        "Я создан, чтобы помочь Вам находить и наслаждаться закатами, где бы Вы ни находились.\n\n" +
                        "Если у Вас возникнут вопросы или Вам потребуется помощь, свяжитесь с автором бота в Telegram.\n\n" +
                        "_Пусть каждый Ваш вечер будет украшен великолепным закатом!❤_", true, true);
            } else if (text.equalsIgnoreCase("/sunset")) {
                if (city != null) getSunsetInfo(chatId, city);
                else sendMsg(chatId, "Сначала введите город|поселок командой */changecity*", false, true);

            } else if (text.equalsIgnoreCase("/weather")) {
                if (city != null) getWeather(chatId, city);
                else sendMsg(chatId, "Сначала введите город|поселок командой */changecity*", false, true);

            } else {
                sendMsg(chatId, "Для получения всех нужных команд пишите */help*", false, true);
            }
        }
    }
    public static String getSunsetVerdict(int sunsetChance, String cloudType) {
        String verdict;
        if (sunsetChance >= 80) verdict = "Приготовьтесь слепнуть от красоты!";
        else if (sunsetChance >= 50) verdict = "Он может не быть самым лучшим в вашей жизни, но его стоит увидеть, все закаты чудесны!";
        else verdict = "Не стоит надеяться на красоту сегодня, но позже погода обязательно наладится и принесет подарок!";

        return "Сегодня ожидаются _" + cloudType + "_. Процент на красивый закат: *" + sunsetChance + "%*\n" + verdict +
                "\n_Помните, что погода может преподнести сюрпризы!_";
    }
    private void getWeather(Long chatId, String city) {
        try {
            String weatherApiUrl = "https://api.openweathermap.org/data/2.5/weather?q=" + URLEncoder.encode(city, StandardCharsets.UTF_8) + "&appid=" + apiKeyOWM + "&units=metric";
            String timezoneApiUrl = "http://api.timezonedb.com/v2.1/get-time-zone?key="+ apiKeyTZ +"&format=json&by=position&lat=LATITUDE&lng=LONGITUDE";
            JSONObject weatherJson = makeApiRequest(weatherApiUrl);
            if (weatherJson == null) {
                sendMsg(chatId, "Ошибка получения данных о погоде.", false, true);
                return;
            }
            JSONObject coordJson = weatherJson.getJSONObject("coord");
            double lat = coordJson.getDouble("lat");
            double lon = coordJson.getDouble("lon");

            timezoneApiUrl = timezoneApiUrl.replace("LATITUDE", String.valueOf(lat)).replace("LONGITUDE", String.valueOf(lon));
            JSONObject timezoneJson = makeApiRequest(timezoneApiUrl);
            if (timezoneJson == null) {
                sendMsg(chatId, "Ошибка получения данных о часовом поясе.", false, true);
                return;
            }
            long sunsetUnix = weatherJson.getJSONObject("sys").getLong("sunset");
            String timezoneId = timezoneJson.getString("zoneName");

            LocalTime sunsetTime = Instant.ofEpochSecond(sunsetUnix)
                    .atZone(ZoneId.of(timezoneId))
                    .toLocalTime();
            String forecastApiUrl = "https://api.openweathermap.org/data/2.5/forecast?q=" + URLEncoder.encode(city, StandardCharsets.UTF_8) + "&appid=" + apiKeyOWM + "&units=metric";
            JSONObject forecastJson = makeApiRequest(forecastApiUrl);
            if (forecastJson == null) {
                sendMsg(chatId, "Ошибка получения прогноза погоды.", false, true);
                return;
            }
            JSONArray forecastArray = forecastJson.getJSONArray("list");
            JSONObject closestForecast = null;
            long closestTimeDiff = Long.MAX_VALUE;
            for (int i = 0; i < forecastArray.length(); i++) {
                JSONObject forecast = forecastArray.getJSONObject(i);
                long forecastTimeUnix = forecast.getLong("dt");
                LocalTime forecastTime = Instant.ofEpochSecond(forecastTimeUnix)
                        .atZone(ZoneId.of(timezoneId))
                        .toLocalTime();
                long timeDiff = Math.abs(Duration.between(sunsetTime, forecastTime).getSeconds());
                if (timeDiff < closestTimeDiff) {
                    closestTimeDiff = timeDiff;
                    closestForecast = forecast;
                }
            }
            if (closestForecast == null) {
                sendMsg(chatId, "Не удалось найти прогноз на время заката.", false, true);
                return;
            }
            JSONObject mainInfo = closestForecast.getJSONObject("main");
            double temperature = mainInfo.getDouble("temp");
            double pressure = mainInfo.getDouble("pressure");
            double humidity = mainInfo.getDouble("humidity");
            JSONObject windInfo = closestForecast.getJSONObject("wind");
            double windSpeed = windInfo.getDouble("speed");
            JSONObject cloudsInfo = closestForecast.getJSONObject("clouds");
            int clouds = cloudsInfo.getInt("all");

            JSONObject weather = closestForecast.getJSONArray("weather").getJSONObject(0);
            String cloudDescription = weather.getString("description");

            String cloudType = identifyCloudType(cloudDescription, temperature, humidity, windSpeed * 3.6);
            int sunsetChance = calculateSunsetChance(windSpeed, (int) humidity, clouds, cloudType);

            sendMsg(chatId, "Прогноз погоды на время заката в " + city + ":\n" +
                    "\n*Время заката:* " + sunsetTime.format(DateTimeFormatter.ofPattern("HH:mm")) +
                    "\n\n_" + cloudType + "_" +
                    "\n*Облачность: *" + clouds + "%" +
                    "\n\n*Влажность:* " + humidity + "%" +
                    "\n*Скорость ветра:* " + String.format(Locale.US, "%.2f", windSpeed * 3.6) + " км/ч" +
                    "\n*Температура:* " + String.format(Locale.US, "%.1f", temperature) + "°C" +
                    "\n*Давление:* " + String.format(Locale.US, "%.1f", pressure) + " гПа" +
                    "\n\n*Шанс красивого заката:* " + sunsetChance + "%", false, true);
        } catch (JSONException e) {
            sendMsg(chatId, "Произошла ошибка: " + e.getMessage(), false, true);
        }
    }
    private void getSunsetInfo(Long chatId, String city) {
        try {
            String weatherApiUrl = "https://api.openweathermap.org/data/2.5/weather?q=" + URLEncoder.encode(city, StandardCharsets.UTF_8) + "&appid=" + apiKeyOWM + "&units=metric";
            String timezoneApiUrl = "http://api.timezonedb.com/v2.1/get-time-zone?key="+ apiKeyTZ +"&format=json&by=position&lat=LATITUDE&lng=LONGITUDE";
            JSONObject weatherJson = makeApiRequest(weatherApiUrl);
            if (weatherJson == null) {
                sendMsg(chatId, "Ошибка получения данных о погоде.", false, true);
                return;
            }

            JSONObject coordJson = weatherJson.getJSONObject("coord");
            double lat = coordJson.getDouble("lat");
            double lon = coordJson.getDouble("lon");

            timezoneApiUrl = timezoneApiUrl.replace("LATITUDE", String.valueOf(lat)).replace("LONGITUDE", String.valueOf(lon));
            JSONObject timezoneJson = makeApiRequest(timezoneApiUrl);
            if (timezoneJson == null) {
                sendMsg(chatId, "Ошибка получения данных о часовом поясе.", false, true);
                return;
            }

            long sunsetUnix = weatherJson.getJSONObject("sys").getLong("sunset");
            String timezoneId = timezoneJson.getString("zoneName");

            LocalTime sunsetTime = Instant.ofEpochSecond(sunsetUnix)
                    .atZone(ZoneId.of(timezoneId))
                    .toLocalTime();

            String forecastApiUrl = "https://api.openweathermap.org/data/2.5/forecast?q=" + URLEncoder.encode(city, StandardCharsets.UTF_8) + "&appid=" + apiKeyOWM + "&units=metric";
            JSONObject forecastJson = makeApiRequest(forecastApiUrl);
            if (forecastJson == null) {
                sendMsg(chatId, "Ошибка получения прогноза погоды.", false, true);
                return;
            }

            JSONArray forecastArray = forecastJson.getJSONArray("list");
            JSONObject closestForecast = null;
            long closestTimeDiff = Long.MAX_VALUE;
            for (int i = 0; i < forecastArray.length(); i++) {
                JSONObject forecast = forecastArray.getJSONObject(i);
                long forecastTimeUnix = forecast.getLong("dt");
                LocalTime forecastTime = Instant.ofEpochSecond(forecastTimeUnix)
                        .atZone(ZoneId.of(timezoneId))
                        .toLocalTime();

                long timeDiff = Math.abs(Duration.between(sunsetTime, forecastTime).getSeconds());
                if (timeDiff < closestTimeDiff) {
                    closestTimeDiff = timeDiff;
                    closestForecast = forecast;
                }
            }
            if (closestForecast == null) {
                sendMsg(chatId, "Не удалось найти прогноз на время заката.", false, true);
                return;
            }
            JSONObject mainInfo = closestForecast.getJSONObject("main");
            double temperature = mainInfo.getDouble("temp");
            double humidity = mainInfo.getDouble("humidity");
            JSONObject windInfo = closestForecast.getJSONObject("wind");
            double windSpeed = windInfo.getDouble("speed");
            JSONObject cloudsInfo = closestForecast.getJSONObject("clouds");
            int clouds = cloudsInfo.getInt("all");

            JSONObject weather = closestForecast.getJSONArray("weather").getJSONObject(0);
            String cloudDescription = weather.getString("description");

            String cloudType = identifyCloudType(cloudDescription, temperature, humidity, windSpeed * 3.6);
            int sunsetChance = calculateSunsetChance(windSpeed, (int) humidity, clouds, cloudType);

            sendMsg(chatId, getSunsetVerdict(sunsetChance, cloudType), false, true);
        } catch (JSONException e) { sendMsg(chatId, "Произошла ошибка: " + e.getMessage(), false, true); }
    }
    public static int calculateSunsetChance(double windSpeed, int humidity, int cloudiness, String cloudType) {
        int chance = 45;

        if (windSpeed > 15)  chance -= 20;
        else if (windSpeed > 10) chance -= 10;
        else if (windSpeed < 5) chance += 5;

        if (humidity > 85) chance -= 15;
        else if (humidity > 70) chance -= 10;
        else if (humidity < 40) chance += 5;

        if (cloudType.contains("Слоисто-дождевые облака") || cloudType.contains("Кучево-дождевые облака")) chance -= 25;
        else if (cloudType.contains("Слоистые облака") || cloudType.contains("Высокослоистые облака") || cloudType.contains("Отсутствие облаков")) chance -= 15;
        else if (cloudType.contains("Высококучевые облака")) chance -= 10;
        else if (cloudType.contains("Перистые облака") || cloudType.contains("Кучевые облака"))  chance += 20;


        if (cloudiness >= 25 && cloudiness <= 50) chance += 10;
        if (cloudiness >= 5 && cloudiness < 25) chance += 5;
        if (cloudiness >= 5 && cloudiness < 25) chance += 10;
        if (cloudiness > 75) chance -= 15;

        if (chance > 89) chance = 89;
        if (chance < 5) chance = 5;

        return chance;
    }
    public static String identifyCloudType(String description, double temperature, double humidity, double windSpeed) {
        description = description.toLowerCase();

        if (description.contains("clear") || description.contains("sky is clear")) return "Отсутствие облаков";

        if (description.contains("light rain")) return "Слоисто-дождевые облака";

        if (description.contains("thunderstorm")) return "Кучево-дождевые облака";

        if (description.contains("heavy rain") || description.contains("shower rain")) return "Кучево-дождевые облака";

        if (description.contains("rain")) return "Слоисто-дождевые облака";

        if (description.contains("overcast") || description.contains("cloudy") ||
                description.contains("partly cloudy") || description.contains("scattered clouds") ||
                description.contains("broken clouds") || description.contains("few clouds")) {
            if (temperature > 10 && humidity > 60 && windSpeed > 10) return "Перистые облака";
            else return "Кучевые облака";

        }
        if (description.contains("altocumulus")) return "Высококучевые облака";

        if (description.contains("altostratus")) return "Высокослоистые облака";

        if (description.contains("stratus")) return "Слоистые облака";

        if (description.contains("stratocumulus")) return "Слоисто-кучевые облака";

        if (description.contains("cumulus")) return "Кучевые облака";

        return "Тип облаков:" + description + " (если это вылезло, сообщите автору)";
    }
    private JSONObject makeApiRequest(String apiUrl) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(apiUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");

            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder response = new StringBuilder();
                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();
                return new JSONObject(response.toString());
            } else {
                System.out.println("Ошибка: сервер вернул код " + responseCode);
            }
        } catch (IOException | JSONException e) { System.out.println("Произошла ошибка при выполнении запроса: " + e.getMessage());
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
        return null;
    }
    private boolean isValidCity(String cityName) {
        try {
            String apiUrl = "https://nominatim.openstreetmap.org/search?q=" + URLEncoder.encode(cityName, StandardCharsets.UTF_8) + "&format=json&limit=1&accept-language=ru";
            URL url = new URL(apiUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");

            int responseCode = connection.getResponseCode();

            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder response = new StringBuilder();
                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();

                JSONArray jsonArray = new JSONArray(response.toString());
                if (!jsonArray.isEmpty()) {
                    JSONObject cityInfo = jsonArray.getJSONObject(0);
                    String foundCityName = cityInfo.getString("display_name");
                    return foundCityName.toLowerCase().contains(cityName.toLowerCase());
                }
            }
        } catch (IOException | org.json.JSONException e) { e.printStackTrace(); } return false;
    }
    private void clearUserCities() {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement("DELETE FROM UserCities")) {
            pstmt.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }
    public void getStats(Long chatId) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement("SELECT * FROM UserCities")) {
            ResultSet rs = pstmt.executeQuery();
            StringBuilder response = new StringBuilder("Зарегистрированные пользователи и их города:\n\n");
            while (rs.next()) {
                long userChatId = rs.getLong("chatId");
                String city = rs.getString("city");
                String username = getUsernameById(userChatId);
                response.append("@").append(username).append(" Город: ").append(city).append("\n");
            }
            sendMsg(chatId, response.toString(), false, false);
        } catch (SQLException e) {
            sendMsg(chatId, "Произошла ошибка при получении данных: " + e.getMessage(), false, true);
        } catch (TelegramApiException e) {
            sendMsg(chatId, "Произошла ошибка при обращении к Telegram API: " + e.getMessage(), false, true);
        }
    }
    public String getUsernameById(long userId) throws TelegramApiException {
        GetChat getChatRequest = new GetChat();
        getChatRequest.setChatId(userId);
        Chat chat = execute(getChatRequest);
        return chat.getUserName();
    }
    public void broadcastMessage(String message) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement("SELECT chatId FROM UserCities")) {
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                long chatId = rs.getLong("chatId");
                sendMsg(chatId, message, false, false);
            }
        } catch (SQLException e) { System.out.println("Произошла ошибка при попытке рассылки сообщений: " + e.getMessage()); }
    }
    public void sendMsg(long chatId, String text, boolean addButton, boolean Markdown) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        sendMessage.setText(text);
        sendMessage.enableMarkdown(Markdown);

        if (addButton) {
            InlineKeyboardMarkup keyboardMarkup = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> rows = new ArrayList<>();
            List<InlineKeyboardButton> row = new ArrayList<>();

            InlineKeyboardButton button = new InlineKeyboardButton();
            button.setText("Связаться с автором");
            button.setUrl("https://t.me/jdjdjddjhddj");

            row.add(button);
            rows.add(row);

            keyboardMarkup.setKeyboard(rows);
            sendMessage.setReplyMarkup(keyboardMarkup);
        }

        try {
            execute(sendMessage);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }
    public static void main(String[] args) throws TelegramApiException {
        TelegramBotsApi telegramBotsApi = new TelegramBotsApi(DefaultBotSession.class);
        try {
            telegramBotsApi.registerBot(new SunsetTGBot());
            System.out.println("Бот запущен.");
        } catch (TelegramApiException e) { e.printStackTrace(); }
    }

    public String getBotUsername() { return BOT_USERNAME; }
    public String getBotToken() { return BOT_TOKEN; }
}