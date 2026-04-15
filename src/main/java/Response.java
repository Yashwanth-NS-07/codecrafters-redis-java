import java.util.ArrayList;
import java.util.List;

public class Response {

    private final List<String> parameterList;

    public Response() {
        this.parameterList = new ArrayList<>();
    }

    public void add(String parameter) {
        parameterList.add(parameter);
    }

    public String getParameter(int i) {
        return parameterList.get(i);
    }

    public int getParameterCount() {
        return this.parameterList.size();
    }
}
