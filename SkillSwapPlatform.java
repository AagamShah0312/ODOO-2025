import java.util.*;

public class SkillSwapPlatform {
    public static void sendSwapRequest(User from, User to) {
        SwapRequest request = new SwapRequest(from, to);
        to.swapRequests.add(request);
        System.out.println(from.name + " sent a swap request to " + to.name);
    }

    public static void acceptSwapRequest(User user, int index) {
        if (index >= 0 && index < user.swapRequests.size()) {
            user.swapRequests.get(index).status = "accepted";
            System.out.println(user.name + " accepted swap request from " + user.swapRequests.get(index).from.name);
        }
    }
}