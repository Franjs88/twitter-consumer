package master.storm.twitterapp;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.ParseException;
import java.util.Properties;
import org.scribe.builder.ServiceBuilder;
import org.scribe.builder.api.TwitterApi;
import org.scribe.model.OAuthRequest;
import org.scribe.model.Response;
import org.scribe.model.Token;
import org.scribe.model.Verb;
import org.scribe.oauth.OAuthService;

/**
 * This is the Twitter Streaming API Reader Class This program connects with
 * Twitter via Socket, and reads tweets on demand of other request coming to the
 * server implemented rigth here.
 *
 * @author Fco. Javier Sánchez Carmona
 */
public class TwitterApp {

    private final String STREAMING_API_URL_FILTER;
    private final OAuthService service;
    private final Token accessToken;
    private BufferedReader reader;
    //private static Integer executionMode;
    private static String path;

    public TwitterApp(String userKey, String userSecret, String token, String tokenSecret) {
        this.STREAMING_API_URL_FILTER = "https://stream.twitter.com/1.1/statuses/filter.json";
        service = new ServiceBuilder()
                .provider(TwitterApi.class)
                .apiKey(userKey)
                .apiSecret(userSecret)
                .build();
        accessToken = new Token(token, tokenSecret);
    }

    public void connect(String csvFilter) {
        OAuthRequest request = new OAuthRequest(Verb.POST, STREAMING_API_URL_FILTER);
        request.addHeader("version", "HTTP/1.1");
        request.addHeader("host", "stream.twitter.com");
        request.setConnectionKeepAlive(true);
        request.addHeader("user-agent", "Twitter Stream Reader");
        request.addBodyParameter("track", csvFilter); //Set keywords you'd like to track here
        service.signRequest(accessToken, request);
        Response response = request.send();

        //Create a reader to read Twitter's stream
        reader = new BufferedReader(new InputStreamReader(response.getStream()));
    }

    public void disconnect() throws IOException {
        reader.close();
    }

    public String getTweet(int executionMode) throws IOException, ParseException {
        String line = null;
        if (executionMode == 1) {
            System.out.println("Execution mode = From file " + executionMode);
            File file = new File(TwitterApp.path);
            //Create a reader to read file stream
            BufferedReader fileReader = new BufferedReader(new FileReader(file));

            while (line == null || line.length() <= 0) {
                line = fileReader.readLine();
            }
            fileReader.close();
        } else if (executionMode == 2) {
            System.out.println("Execution mode = from twitter " + executionMode);

            while (line == null || line.length() <= 0) {
                line = reader.readLine();
            }
        } else {
            throw new IllegalArgumentException(
                    "Bad params! executionMode only accepts 1 or 2 option numbers");
        }
        return line;
    }

    // Main Args params go this way:
    // 1) IP address 2) Port 3) execution mode
    // 4) Path to tweets file 
    // 5) Twitter username 6) Twitter user secret 7) Twitter app token
    // 8) Twitter app secret 
    // 9) Query Twitter you want to search for
    public static void main(String[] args) throws IOException, ParseException {

        Properties p = new Properties();
        p.load(new FileInputStream(new File("TwitterAppParams.property")));
        for (String key : p.stringPropertyNames()) {

            System.out.println("key=" + key + ", value=" + p.getProperty(key));
        }

        // Server arguments:
        InetAddress IP = InetAddress.getByName(p.getProperty("ip_address"));
        Integer port = Integer.parseInt(p.getProperty("port_number"));

        // Maximum simultaneous connections set to 100
        ServerSocket server = new ServerSocket(port, 100, IP);
        System.out.println("Magic happens on: "
                        + server.getInetAddress().getHostName() + ":"
                        + server.getLocalPort());

        // set the execution mode
        int executionMode = Integer.parseInt(p.getProperty("execution_mode"));

        // Path to tweets file
        TwitterApp.path = p.getProperty("path_to_tweets_file");

        // Twitter credentials
        String userKey = p.getProperty("twitter_username");
        String userSecret = p.getProperty("twitter_user_secret");
        String token = p.getProperty("twitter_app_token");
        String tokenSecret = p.getProperty("twitter_app_secret");

        // Twitter query
        String queryTwitter = p.getProperty("twitter_query");

        // We construct the TwitterConsumer with given parameters
        TwitterApp tr = new TwitterApp(userKey, userSecret, token, tokenSecret);
        tr.connect(queryTwitter);

        String tweet;

        //We start the server
        try {
            while (true) {
                //if a connection is established, we serve the tweets
                try (Socket socket = server.accept()) {
                    tweet = tr.getTweet(executionMode);
                    PrintWriter out
                            = new PrintWriter(socket.getOutputStream(), true);
                    out.println(tweet);
                    System.out.println("Tweet sended: "+tweet);
                }
            }

        } catch (IOException | ParseException e) {
            e.printStackTrace();
        } finally {
            server.close();
            tr.disconnect();
        }
    }
}
