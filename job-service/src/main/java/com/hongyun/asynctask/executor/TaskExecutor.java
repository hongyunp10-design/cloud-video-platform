package  com.hongyun.asynctask.executor;

import com.hongyun.asynctask.model.Task;
import com.hongyun.asynctask.model.TaskType;

public interface TaskExecutor  {
    TaskType supportedType();

    void execute(Task task);
}