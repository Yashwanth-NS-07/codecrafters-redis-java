import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ListStore {
    private static final Map<String, List<String>> map;
    static {
        map = new HashMap<>();
    }

    public static void add(String listName, String value) {
        map.putIfAbsent(listName, new ArrayList<>());
        map.get(listName).add(value);
    }
    public static int size(String listName) {
        if(map.containsKey(listName)) {
            return map.get(listName).size();
        }
        return -1;
    }
}
