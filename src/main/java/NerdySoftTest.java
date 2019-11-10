public class NerdySoftTest {
    public static void main(String[] args){
        TaskManager taskManager = new TaskManager();
        taskManager.registerUser("man@gmail.com","password");
        taskManager.registerUser("igr@gmail.com","password");
        taskManager.loginUser("man@gmail.com","password");
        taskManager.addTask("First task");
        taskManager.printAllCollections();
    }
}
