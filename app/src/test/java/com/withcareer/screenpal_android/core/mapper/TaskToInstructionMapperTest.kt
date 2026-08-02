package com.withcareer.screenpal_android.core.mapper

import com.withcareer.screenpal_android.data.preference.Task
import com.withcareer.screenpal_android.data.preference.TaskStep
import com.withcareer.screenpal_android.data.preference.TaskState
import org.junit.Assert.assertEquals
import org.junit.Test

class TaskToInstructionMapperTest {

    @Test
    fun map_filtersOutNonJsonActions() {
        // Arrange
        val task = Task(
            id = "task1",
            prompt = "Test Task",
            createdAt = 1234567890L,
            steps = listOf(
                TaskStep(thought = "Planning", action = "Generate Plan"),
                TaskStep(thought = "Executing", action = "{\"action_type\":\"click\",\"target\":\"button\"}"),
                TaskStep(thought = "Thinking", action = "Just thinking")
            ),
            state = null
        )

        // Act
        val result = TaskToInstructionMapper.map(task)
        val steps = result.second

        // Assert
        // We expect only the valid JSON action to be preserved
        // Currently (before fix), it probably preserves "Generate Plan" and "Just thinking" too if they are not empty
        // So this test is expected to fail if I assert size = 1, or pass if I assert size = 3 (reproduction)

        // Let's assert what we WANT (size = 1) to confirm it fails (TDD)
        // Note: "Generate Plan" is the specific one causing issues.

        val jsonSteps = steps.filter { it.actionType == "ATOMIC" }
        assertEquals("Should only have 1 valid atomic step", 1, jsonSteps.size)
        assertEquals("{\"action_type\":\"click\",\"target\":\"button\"}", jsonSteps[0].actionParams)
    }
}
