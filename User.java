import java.util.*;

public class User {
    String name;
    String location;
    String profilePhoto;
    Set<String> skillsOffered = new HashSet<>();
    Set<String> skillsWanted = new HashSet<>();
    String availability;
    boolean isPublic = true;
    List<SwapRequest> swapRequests = new ArrayList<>();
    List<String> feedbackList = new ArrayList<>();

    public User(String name) {
        this.name = name;
    }

    void addFeedback(String feedback) {
        feedbackList.add(feedback);
    }

    @Override
    public String toString() {
        return "Name: " + name + ", Skills Offered: " + skillsOffered + ", Skills Wanted: " + skillsWanted + ", Public: " + isPublic;
    }
}