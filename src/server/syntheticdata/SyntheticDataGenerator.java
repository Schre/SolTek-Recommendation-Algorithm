package server.syntheticdata;

import server.database.queryengine.QueryExecutor;
import server.network.FollowerRecommendationSystem;
import server.network.NetworkNode;
import server.network.RelatednessMatrix;
import server.shared.SharedObjects;

import java.io.*;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.*;

/***
 * This will generate synthetic data, upload the data to our database,
 * and output it to the server.syntheticdata.sql file
 */

// TODO: Output our network to server.syntheticdata.sql

/** TODO: Maybe we should not use NetworkNode and create a network
 *  that creates all of the attributes of a user to make adding them
 *  to the database easier. The idea of a network node is that the uid
 *  corresponds to an already existing user in our database. If we are
 *  creating data then this case does not hold true.
 */
public class SyntheticDataGenerator {

    private static List<String> names = new ArrayList<>();

    // Initializes names
    private static void loadNames() throws FileNotFoundException, IOException {
        FileReader reader = new FileReader("src/server/syntheticdata/names.txt");
        BufferedReader br = new BufferedReader(reader);
        String name;

        while ((name = br.readLine()) != null) {
            names.add(name);
        }

        br.close();
    }

    // Returns a random index of the list
    private static String getRandom(List<String> lst) {
        Random rand = new Random();
        return names.get(Math.abs(rand.nextInt()) % lst.size());
    }

    // arg1 = number of nodes to create in our network (parameter nodeCount)
    // arg2 = density of network D (Probability of connecting two users if the relatedness was 1)
    public static void main(String[] args) {

        // Set up connection pool so that we can execute server.queries
        SharedObjects.initialize();
        RelatednessMatrix.initialize();

        // TODO: Clear all of the tables in the database!!!


        List<String> fields = new ArrayList<>(RelatednessMatrix.getSupportedFields());
        Set<NetworkNode> data = new HashSet<>();

        long nodeCount = Integer.parseInt(args[0]);
        double D = Double.parseDouble(args[1]);
        boolean writeToDB = false;

        if (args.length == 3) {
            writeToDB = Boolean.parseBoolean(args[2]);
        }
        /***
         * Step 1. Generate all of the nodes in our network, randomly assigning each of the nodes
         * a profession according to our set of fields.
         */

        System.out.println("Creating synthetic network where nodeCount=" + nodeCount + "" +
                ", Density=" + D);

        System.out.println("Creating nodes...");

        Random r = new Random();

        NetworkNode test = null;
        for (long i = 0; i < nodeCount; ++i) {

            // Generate a unique uid
            String uid = UUID.randomUUID().toString().substring(0, 20);

            // Generate a random field
            int randomIndex = Math.abs(r.nextInt()) % fields.size();

            String randField = fields.get(randomIndex);

            // create node
            NetworkNode node = new NetworkNode(uid, randField);
            test = node;
            data.add(node);

        }

        /**
         * Step 2. Randomly connect C nodes to other nodes in the network with a probability for each
         * connection determined by our RelatednessMatrix (and mutual followers?)
         */

        long numConnections = 0;
        System.out.println("Connecting nodes...");
        for (NetworkNode node : data) {

            for (NetworkNode adj : data) {

                double connectProbability = RelatednessMatrix.getRelatedness(node.getField(), adj.getField()) * D; // Relatedness * Network Density

                /* Do not connect */
                if (r.nextFloat() > connectProbability) {
                    continue;
                }

                // Add follower
                node.addFollowing(adj);
                ++numConnections;
            }
        }

        System.out.println("number of connections created: " + numConnections);

        /***
         * Step 3. Insert all of the nodes into the database
         * and randomly make up names for required fields
         */

        System.out.println("Writing nodes to database...");
        try {
            loadNames();
        }
        catch (FileNotFoundException fnfe) {
            System.out.println("FATAL: File not found. " + fnfe.getMessage());
            return;
        }
        catch (IOException ioe) {
            System.out.println("FATAL: IO Exception reading file. " + ioe.getMessage());
        }

        long count = 0;
        Character[] genders = {'M', 'F', 'O'};

        BufferedWriter bw;

        try {
            bw = new BufferedWriter(new FileWriter("src/server/syntheticdata/syntheticdata.sql"));
        }
        catch (IOException ioe) {
            System.out.println("FATAL: Could not open file output file. " + ioe.getMessage());
            return;
        }

        for (NetworkNode node : data) {
            ++count;

            String user_id = node.getUID();
            String field = node.getField();

            // email does not matter
            String email = UUID.randomUUID().toString().substring(0, 20);
            String username = "syntheticUser" + Long.toString(count);
            String password = "synthetic";
            String first_name = getRandom(names);
            String last_name = getRandom(names);
            Character gender = genders[Math.abs((new Random()).nextInt()) % genders.length];
            String dateCreated = new SimpleDateFormat("yyyy-MM-dd").format((new Date()));

            String query = "INSERT INTO Users (user_id, email, username, password, first_name," +
                    "last_name, gender, date_created, field) VALUES (" +
                    "\"" + user_id + "\"" + ',' + "\"" + email + "\"" + ',' + "\"" + username + "\"" + ',' + "\"" + password + "\"" + ',' + "\"" +
                    first_name + "\"" + ',' + "\"" + last_name + "\"" + ',' + "\"" + gender + "\"" + ','
                    + "\"" + dateCreated  + "\"" + ',' + "\"" + field + "\"" +
                    ")";

            try {
                bw.write(query + "\n");
                if (writeToDB == true) {
                    try {
                        QueryExecutor.execute(query);
                    }
                    catch (SQLException sqle) {
                        System.out.println("Failed to insert user to database. " + sqle);
                    }
                }
            }
            catch (IOException ioe) {
                System.out.println("Error writing query");
            }

            //System.out.println(query);
        }

        try {
            bw.close();
        }
        catch (IOException ioe) {
            System.out.println("FATAL: Failed to close BufferedWriter. " + ioe);
            return;
        }

        /***
         * Step 4. Add all of the follower information to the
         * database
         */

        System.out.println("Writing connections to database...");
        for (NetworkNode node : data) {
            if (nodeCount % 1000 == 0) {
                System.out.println(nodeCount + " remaining nodes to write connections remaining...");
            }
            --nodeCount;
            String user_id = node.getUID();
            /* Add all of the followers into the db */
            for (NetworkNode adj : node.getUsersFollowed()) {
                String following_id = adj.getUID();
                String query = "INSERT INTO Followings (user_id, following_id)  VALUES (" +
                        "\"" + user_id + "\"" + "," + "\"" + following_id + "\""
                        + ")";

                //try {
                    //bw.append(query + "\n");
                    if (writeToDB == true) {
                        try {
                            QueryExecutor.execute(query);
                        } catch (SQLException sqle) {
                            System.out.println("Failed to insert follower to database. " + sqle);
                        }
                    }
              /*  } catch (IOException ioe) {
                    System.out.println("Error writing query");
                }

                System.out.println(query);*/
            }

            /*try {
                bw.close();
            } catch (IOException ioe) {
                System.out.println("FATAL: Failed to close BufferedWriter. " + ioe);
                return;
            }*/
        }

        FollowerRecommendationSystem frs = new FollowerRecommendationSystem(test);
        List<NetworkNode> top10 = frs.getTopKRecommendations(10);
        System.out.println("Finished generating synthetic data");

    }
}
