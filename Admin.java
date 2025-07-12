import java.util.*;

public class Admin {
    Set<String> bannedUsers = new HashSet<>();
    List<String> platformMessages = new ArrayList<>();

    public void rejectInappropriateSkill(User user, String skill) {
        user.skillsOffered.remove(skill);
        System.out.println("Skill '" + skill + "' removed from user " + user.name);
    }

    public void banUser(User user) {
        bannedUsers.add(user.name);
        System.out.println("User " + user.name + " has been banned.");
    }

    public void monitorSwaps(List<User> users) {
        for (User user : users) {
            for (SwapRequest sr : user.swapRequests) {
                System.out.println(sr.from.name + " -> " + sr.to.name + ": " + sr.status);
            }
        }
    }

    public void sendPlatformMessage(String message) {
        platformMessages.add(message);
        System.out.println("Broadcast message: " + message);
    }

    public void downloadReports(List<User> users) {
        System.out.println("User Activity Report:");
        for (User user : users) {
            System.out.println(user.name + " Feedbacks: " + user.feedbackList);
        }
    }
}