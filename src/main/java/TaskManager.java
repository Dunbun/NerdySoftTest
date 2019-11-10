import com.mongodb.*;
import com.mongodb.MongoClient;
import com.mongodb.client.*;
import org.bson.Document;
import org.bson.types.ObjectId;
import java.util.ArrayList;
public class TaskManager {
    private final String databaseName = "TaskManagerDB";
    private final String usersCollectionName = "Users";
    private final String tasksCollectionName = "Tasks";
    private final String taskAccessCollectionName = "TaskAccess";
    private MongoClient mongoClient;
    private MongoDatabase mongoDatabase;
    private MongoCredential mongoCredential;
    private MongoCollection<Document> usersCollection;
    private MongoCollection<Document> tasksCollection;
    private MongoCollection<Document> taskAccessCollection;
    private String userEmail;
    private String userPassword;

    TaskManager(){
        mongoClient = new MongoClient( "localhost" , 27017 );
        if(!databaseIsFound()){
           mongoCredential = MongoCredential.createCredential("Admin", databaseName,
                "password".toCharArray());
        }
        mongoDatabase = mongoClient.getDatabase(databaseName);
        usersCollection = mongoDatabase.getCollection(usersCollectionName);
        tasksCollection = mongoDatabase.getCollection(tasksCollectionName);
        taskAccessCollection = mongoDatabase.getCollection(taskAccessCollectionName);
    }

    public void registerUser(String userEmail, String userPassword){
        if(!userIsFound(userEmail)) {
            ArrayList<String> listOfTasks = new ArrayList<>();
            Document userDocument = new Document()
                    .append("email", userEmail)
                    .append("password", userPassword.hashCode())
                    .append("listOfTasks", listOfTasks);
            usersCollection.insertOne(userDocument);
        }else{
            System.out.println("Such user already exists");
        }
    }

    public void loginUser(String userEmail, String userPassword){
        MongoCursor cursor = whereQuery("email", userEmail, usersCollection);
        if(cursor.hasNext()){
            Document document = (Document) cursor.next();
            String hashedUserPassword = String.valueOf(userPassword.hashCode());
            if(hashedUserPassword.equals(String.valueOf(document.get("password")))){
                this.userEmail = userEmail;
                this.userPassword = userPassword;
                System.out.println("logged in");
            }else {
                System.out.println("Wrong password");
            }
        }else{
            System.out.println("There is no such user");
        }
    }

    public void addTask(String taskContent){
        Document document = new Document()
                .append("taskContent", taskContent);
        tasksCollection.insertOne(document);
        String taskID = String.valueOf(document.get("_id"));
        document = new Document()
                .append("taskID", taskID)
                .append("accessFor", userEmail)
                .append("sharedBy","");
        taskAccessCollection.insertOne(document);
        MongoCursor cursor = whereQuery("email", userEmail, usersCollection);
        document = (Document) cursor.next();
        ArrayList<String>listOfTasks = (ArrayList<String>) document.get("listOfTasks");
        listOfTasks.add(taskID);
        usersCollection.updateOne(
                new BasicDBObject("email", userEmail),
                new BasicDBObject("$set", new BasicDBObject("listOfTasks", listOfTasks))
        );
    }

    public void editTask(String taskID, String newTaskContent){
        tasksCollection.updateOne(
                new BasicDBObject("_id", new ObjectId(taskID)),
                new BasicDBObject("$set", new BasicDBObject("taskContent", newTaskContent))
        );
    }

    public void deleteTask(String taskID){
        BasicDBObject documentToDelete = new BasicDBObject();
        documentToDelete.append("_id", new ObjectId(taskID));
        tasksCollection.deleteOne(documentToDelete);
        MongoCursor cursor = whereQuery("taskID",taskID,taskAccessCollection);
        while(cursor.hasNext()){
            Document accessDocument = (Document) cursor.next();
            deleteTaskIDFromUserDocument(String.valueOf(accessDocument.get("accessFor")),taskID);
        }
        documentToDelete.clear();
        documentToDelete.append("taskID", taskID);
        taskAccessCollection.deleteMany(documentToDelete);
    }

    public void shareTask(String emailWhomToShare, String taskID){
        MongoCursor cursor = whereQuery("email", emailWhomToShare, usersCollection);
        if(cursor.hasNext()){
            Document userDocument = (Document) cursor.next();
            ArrayList<String> listOfTasks = new ArrayList<>();
            listOfTasks = (ArrayList<String>) userDocument.get("listOfTasks");
            listOfTasks.add(taskID);
            usersCollection.updateOne(
                    new BasicDBObject("email", emailWhomToShare),
                    new BasicDBObject("$set", new BasicDBObject("listOfTasks", listOfTasks))
            );
        Document accessDocument = new Document()
                .append("taskID", taskID)
                .append("accessFor", emailWhomToShare)
                .append("sharedBy", userEmail);
        taskAccessCollection.insertOne(accessDocument);
        }else{
            System.out.println("There is no such user");
        }
    }

    public ArrayList<String> getListOfTasks(){
        ArrayList<String> listOfTasks = new ArrayList<>();
        MongoCursor cursor = whereQuery("email",userEmail, usersCollection);
        if(cursor.hasNext()){
            ArrayList<String> list = new ArrayList<>();
            Document document = (Document) cursor.next();
            list = (ArrayList<String>) document.get("listOfTasks");
            for(int i = 0; i < list.size(); i++) {
                listOfTasks.add(getTaskContent(list.get(i)));
            }
            return listOfTasks;
        }
        return null;
    }

    public void printAllCollections(){
        printCollection(usersCollection);
        printCollection(tasksCollection);
        printCollection(taskAccessCollection);
    }

    private MongoCursor whereQuery(String fieldName, String fieldValue, MongoCollection mongoCollection){
        BasicDBObject query = new BasicDBObject();
        query.put(fieldName, fieldValue);
        FindIterable iterable =  mongoCollection.find(query);
        return iterable.iterator();
    }

    private Boolean databaseIsFound(){
        MongoCursor<String> dbsCursor = mongoClient.listDatabaseNames().iterator();
        while(dbsCursor.hasNext()) {
            if(dbsCursor.next().equals(databaseName))
                return true;
        }
        return false;
    }

    private Boolean userIsFound(String userEmail){
        MongoCursor cursor = whereQuery("email", userEmail, usersCollection);
        if(cursor.hasNext()){
            return true;
        }
        return false;
    }

    private void printCollection(MongoCollection collection){
        MongoIterable iterable =  collection.find();
        MongoCursor cursor = iterable.iterator();
        while(cursor.hasNext()){
            System.out.println(cursor.next());
        }
    }

    private void deleteTaskIDFromUserDocument(String userEmail, String taskID){
        MongoCursor cursor = whereQuery("email", userEmail, usersCollection);
        Document document = (Document) cursor.next();
        ArrayList<String> listOfTasks = (ArrayList<String>) document.get("listOfTasks");
        listOfTasks.remove(new String(taskID));
        usersCollection.updateOne(
                new BasicDBObject("email", userEmail),
                new BasicDBObject("$set", new BasicDBObject("listOfTasks", listOfTasks))
        );
    }

    private String getTaskContent(String taskID){
        String taskContent;
        BasicDBObject query = new BasicDBObject();
        query.put("_id", new ObjectId(taskID));
        FindIterable iterable =  tasksCollection.find(query);
        MongoCursor cursor = iterable.iterator();
        if(cursor.hasNext()){
            Document document = (Document) cursor.next();
            taskContent = (String) document.get("taskContent");
            query.clear();
            query.put("taskID",taskID);
            query.put("accessFor", userEmail);
            iterable =  taskAccessCollection.find(query);
            cursor = iterable.iterator();
            Document accessDocument = (Document) cursor.next();
            String sharedBy = String.valueOf(accessDocument.get("sharedBy"));
            if(sharedBy.equals("")){
                return taskContent;
            }else {
                taskContent = taskContent + " | shared by " + sharedBy;
            }
            return taskContent;
        }
        return "";
    }
}
