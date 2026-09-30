package com.example.rssreader

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.*

enum class Category { WORK, PERSONAL, SCHOOL, OTHER }
enum class Priority { LOW, MEDIUM, HIGH }

data class Task(
    val id: Long,
    val text: String,
    val isDone: Boolean = false,
    val dueDate: Long? = null,
    val category: Category = Category.OTHER,
    val priority: Priority = Priority.MEDIUM
)

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    data object Tasks : Screen("tasks", "Tasks", Icons.Default.List)
    data object Stats : Screen("stats", "Stats", Icons.Default.CheckCircle)
    data object Settings : Screen("settings", "Settings", Icons.Default.Settings)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createNotificationChannel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
            }
        }
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize()) { GetreadyApp() }
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("tasks", "Task Reminders", NotificationManager.IMPORTANCE_HIGH)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GetreadyApp() {
    val context = LocalContext.current
    var tasks by remember { mutableStateOf(loadTasks(context)) }
    var selectedScreen by remember { mutableStateOf<Screen>(Screen.Tasks) }
    var taskText by remember { mutableStateOf("") }
    var selectedDate by remember { mutableStateOf<Long?>(null) }
    var selectedCategory by remember { mutableStateOf(Category.OTHER) }
    var selectedPriority by remember { mutableStateOf(Priority.MEDIUM) }

    fun updateTasks(newTasks: List<Task>) {
        tasks = newTasks
        saveTasks(context, newTasks)
        scheduleNotifications(context, newTasks)
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Getready", fontWeight = FontWeight.Bold) }) },
        bottomBar = {
            NavigationBar {
                listOf(Screen.Tasks, Screen.Stats, Screen.Settings).forEach { screen ->
                    NavigationBarItem(
                        selected = selectedScreen == screen,
                        onClick = { selectedScreen = screen },
                        icon = { Icon(screen.icon, contentDescription = screen.label) },
                        label = { Text(screen.label) }
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (selectedScreen) {
                Screen.Tasks -> TasksScreen(taskText, { taskText = it }, selectedDate, { selectedDate = it },
                    selectedCategory, { selectedCategory = it }, selectedPriority, { selectedPriority = it }, tasks, ::updateTasks)
                Screen.Stats -> StatsScreen(tasks)
                Screen.Settings -> SettingsScreen(tasks, ::updateTasks)
            }
        }
    }
}

@Composable
fun TasksScreen(
    taskText: String, onTextChange: (String) -> Unit,
    selectedDate: Long?, onDateChange: (Long?) -> Unit,
    selectedCategory: Category, onCategoryChange: (Category) -> Unit,
    selectedPriority: Priority, onPriorityChange: (Priority) -> Unit,
    tasks: List<Task>, onTasksChange: (List<Task>) -> Unit
) {
    val context = LocalContext.current
    val dateFormat = SimpleDateFormat("dd MMM HH:mm", Locale.getDefault())
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(value = taskText, onValueChange = onTextChange, label = { Text("New task...") }, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))
        Row { Category.entries.forEach { cat -> FilterChip(selected = selectedCategory == cat, onClick = { onCategoryChange(cat) }, label = { Text(cat.name) }, modifier = Modifier.padding(end = 4.dp)) }}
        Row { Priority.entries.forEach { pri -> FilterChip(selected = selectedPriority == pri, onClick = { onPriorityChange(pri) }, label = { Text(pri.name) }, modifier = Modifier.padding(end = 4.dp)) }}
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = {
                val calendar = Calendar.getInstance()
                DatePickerDialog(context, { _, y, m, d ->
                    TimePickerDialog(context, { _, h, min ->
                        calendar.set(y, m, d, h, min, 0)
                        onDateChange(calendar.timeInMillis)
                    }, 9, 0, true).show()
                }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
            }) { Icon(Icons.Default.DateRange, contentDescription = "Pick Date") }
            if (selectedDate != null) Text("Due: ${dateFormat.format(Date(selectedDate))}")
            Spacer(modifier = Modifier.weight(1f))
            Button(onClick = {
                if (taskText.isNotBlank()) {
                    onTasksChange(tasks + Task(System.currentTimeMillis(), taskText, false, selectedDate, selectedCategory, selectedPriority))
                    onTextChange(""); onDateChange(null)
                }
            }) { Text("Add") }
        }
        LazyColumn(modifier = Modifier.weight(1f).padding(top = 16.dp)) {
            items(tasks.sortedWith(compareBy({it.dueDate ?: Long.MAX_VALUE}, {it.priority})), key = { it.id }) { task ->
                TaskItem(task, tasks, onTasksChange)
            }
        }
    }
}

@Composable
fun TaskItem(task: Task, tasks: List<Task>, onTasksChange: (List<Task>) -> Unit) {
    val dateFormat = SimpleDateFormat("dd MMM HH:mm", Locale.getDefault())
    val priorityColor = when(task.priority) { Priority.HIGH -> Color.Red; Priority.MEDIUM -> Color.Yellow; Priority.LOW -> Color.Green }
    val categoryColor = when(task.category) {
        Category.WORK -> Color(0xFF4CAF50); Category.PERSONAL -> Color(0xFF2196F3);
        Category.SCHOOL -> Color(0xFFFF9800); Category.OTHER -> Color.Gray
    }
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = task.isDone, onCheckedChange = { isChecked ->
                val updated = tasks.toMutableList()
                updated[updated.indexOfFirst { it.id == task.id }] = task.copy(isDone = isChecked)
                onTasksChange(updated)
            })
            Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(priorityColor))
                    Spacer(modifier = Modifier.width(4.dp))
                    Box(modifier = Modifier.clip(CircleShape).background(categoryColor.copy(alpha = 0.2f)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                        Text(task.category.name, style = MaterialTheme.typography.labelSmall)
                    }
                }
                Text(task.text, textDecoration = if (task.isDone) TextDecoration.LineThrough else null)
                if (task.dueDate != null) Text("Due: ${dateFormat.format(Date(task.dueDate))}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
            // FIXED BRACKET HERE
            IconButton(onClick = { onTasksChange(tasks.filter { it.id != task.id }) }) {
                Icon(Icons.Default.Delete, contentDescription = "Delete")
            }
        }
    }
}

@Composable
fun StatsScreen(tasks: List<Task>) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("Your Progress", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(24.dp))
        Text("Total: ${tasks.size}")
        Text("Done: ${tasks.count { it.isDone }}", color = MaterialTheme.colorScheme.primary)
        Text("High Priority: ${tasks.count { it.priority == Priority.HIGH && !it.isDone }}", color = Color.Red)
        Text("Due Soon: ${tasks.count { !it.isDone && it.dueDate != null && it.dueDate < System.currentTimeMillis() + 86400000 }}", color = MaterialTheme.colorScheme.error)
    }
}

@Composable
fun SettingsScreen(tasks: List<Task>, onTasksChange: (List<Task>) -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = { onTasksChange(tasks.filter {!it.isDone }) }, modifier = Modifier.fillMaxWidth()) { Text("Clear Completed") }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = { onTasksChange(emptyList()) }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Delete ALL") }
        Text("Getready v2.0", modifier = Modifier.padding(top = 32.dp))
    }
}

fun scheduleNotifications(context: Context, tasks: List<Task>) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    tasks.forEach { task ->
        val intent = Intent(context, ReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(context, task.id.toInt(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        alarmManager.cancel(pendingIntent)
    }
    tasks.filter { !it.isDone && it.dueDate != null && it.dueDate > System.currentTimeMillis() }.forEach { task ->
        val intent = Intent(context, ReminderReceiver::class.java).apply { putExtra("taskText", task.text); putExtra("taskId", task.id.toInt()) }
        val pendingIntent = PendingIntent.getBroadcast(context, task.id.toInt(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, task.dueDate!!, pendingIntent)
    }
}

fun saveTasks(context: Context, tasks: List<Task>) {
    val prefs = context.getSharedPreferences("getready", Context.MODE_PRIVATE)
    prefs.edit().putString("tasks", Gson().toJson(tasks)).apply()
}

fun loadTasks(context: Context): List<Task> {
    val prefs = context.getSharedPreferences("getready", Context.MODE_PRIVATE)
    val json = prefs.getString("tasks", "[]")
    val type = object : TypeToken<List<Task>>() {}.type
    return Gson().fromJson(json, type)
}