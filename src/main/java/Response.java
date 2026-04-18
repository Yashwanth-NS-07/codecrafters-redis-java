import java.util.ArrayList;
import java.util.List;

public class Response {

    private final List<Object> parameterList;

    public Response() {
        this.parameterList = new ArrayList<>();
    }

    public void add(Object parameter) {
        parameterList.add(parameter);
    }

    public Object getParameter(int i) {
        return parameterList.get(i);
    }

    public int getParameterCount() {
        return this.parameterList.size();
    }
}
