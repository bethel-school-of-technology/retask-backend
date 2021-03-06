package com.retask.game.services;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.retask.game.message.request.DateTimeRangeRequest;
import com.retask.game.message.request.TaskRequest;
import com.retask.game.message.request.TaskStatusRequest;
import com.retask.game.message.response.TaskResponse;
import com.retask.game.model.Task;
import com.retask.game.model.TaskStatus;
import com.retask.game.model.Upload;
import com.retask.game.model.User;
import com.retask.game.model.UserTask;
import com.retask.game.repository.TaskRepository;
import com.retask.game.repository.TaskStatusRepository;
import com.retask.game.repository.UploadRepository;
import com.retask.game.repository.UserTaskRepository;

@Service
public class TaskService {

	@Autowired
	private UserTaskRepository userTaskRepository;
	@Autowired
	private TaskRepository taskRepository;
	@Autowired
	private TaskStatusRepository taskStatusRepository;
	@Autowired
	private UploadRepository uploadRepository;
	@Autowired
	private ToolsService toolsService;

	/**
	 * unComplete a task 
	 * 
	 * @param username
	 * @param taskStatusRequest
	 * @return
	 * @throws ParseException
	 */
	public boolean unCompleteTask(String username, TaskStatusRequest taskStatusRequest) throws ParseException {

		Date completeDate = new SimpleDateFormat("yyyy-MM-dd").parse(taskStatusRequest.getCompleteDate());

		Task task = taskRepository.findTaskById(taskStatusRequest.getTask_id());

		if (!task.getUsername().equals(username)) {
			return false;
		}

		List<TaskStatus> taskStatus = taskStatusRepository.findTaskStatusByTaskId(taskStatusRequest.getTask_id(),
				completeDate, completeDate);

		if (taskStatus.isEmpty()) {
			// already deleted
			return true;
		}

		taskStatusRepository.delete(taskStatus.get(0));

		return true;
	}

	/**
	 * complete a task.
	 * 
	 * @param username
	 * @param taskStatusRequest
	 * @return
	 * @throws ParseException
	 */
	public boolean completeTask(String username, TaskStatusRequest taskStatusRequest) throws ParseException {

		Date completeDate = new SimpleDateFormat("yyyy-MM-dd").parse(taskStatusRequest.getCompleteDate());

		Task task = taskRepository.findTaskById(taskStatusRequest.getTask_id());

		if (!task.getUsername().equals(username)) {
			return false;
		}

		List<TaskStatus> taskStatus = taskStatusRepository.findTaskStatusByTaskId(taskStatusRequest.getTask_id(),
				completeDate, completeDate);

		if (!taskStatus.isEmpty()) {
			// already exists
			return true;
		}

		java.sql.Date sqlDate = new java.sql.Date(completeDate.getTime());

		TaskStatus newTaskStatus = new TaskStatus();
		newTaskStatus.setTask_id(taskStatusRequest.getTask_id());
		newTaskStatus.setCompleteDate(sqlDate);
		newTaskStatus.setCreateDateTime();
		newTaskStatus.setUpdateDateTime();

		taskStatusRepository.save(newTaskStatus);

		return true;
	}

	/**
	 * gets the tasks by the username
	 * 
	 * @param username
	 * @return
	 */
	public Task gettask(String username, Long task_id) {

		Task task = taskRepository.findTaskById(task_id);
		List<Upload> uploads;
		List<TaskResponse> tasksResponse = new ArrayList<TaskResponse>();
		TaskResponse taskResponse = new TaskResponse();
		
		if (task.getUsername().equals(username)) {
			uploads = uploadRepository.findBySourceTypeAndId("task", task_id);
			taskResponse = new TaskResponse(task);
			taskResponse.setUploads(uploads);
		}

		return task;
	}

	
	/**
	 * gets the tasks by the username
	 * 
	 * @param username
	 * @return
	 */
	public List<TaskResponse> getTasksbyUsername(String username) {

		List<UserTask> userTasksList = userTaskRepository.findByUsername(username);
		List<Task> taskList = new ArrayList<Task>();
		List<Upload> uploads;
		List<TaskResponse> tasksResponse = new ArrayList<TaskResponse>();
		TaskResponse taskResponse;

		for (UserTask userTask : userTasksList) {
			taskList.add(userTask.getTask());

			uploads = uploadRepository.findBySourceTypeAndId("task", userTask.getTask_id());

			taskResponse = new TaskResponse(userTask.getTask());

			taskResponse.setUploads(uploads);

			tasksResponse.add(taskResponse);
		}

		return tasksResponse;
	}

	/**
	 * Call this method to get task by a date range.
	 * 
	 * @param open
	 * @param username
	 * @param dateTimeRange
	 * @return
	 * @throws ParseException
	 */
	public List<TaskResponse> getTasksByDateRange(Boolean open, String username, DateTimeRangeRequest dateTimeRange)
			throws ParseException {

		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
		Date startDate = sdf.parse(dateTimeRange.getStartdate());
		Date endDate = sdf.parse(dateTimeRange.getEnddate());

		List<TaskResponse> tasksResponse = new ArrayList<TaskResponse>();
		List<TaskResponse> tasksResponse2 = new ArrayList<TaskResponse>();

		// setup the date ran
		DateTimeRangeRequest workDateTimeRange = new DateTimeRangeRequest();

		long days = toolsService.daysBetweenDates(startDate, endDate) + 1;
		LocalDate lStartDate = startDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
		Date beginDate = null;
		String sBeginDate = null;

		for (int i = 0; i < days; i++) {
			beginDate = Date.from(lStartDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
			sBeginDate = sdf.format(beginDate);

			workDateTimeRange.setStartdate(sBeginDate);
			workDateTimeRange.setEnddate(sBeginDate);
			tasksResponse2 = this.getTasks(open, username, workDateTimeRange);
			tasksResponse.addAll(tasksResponse2);
			lStartDate = lStartDate.plusDays(1);
		}

		return tasksResponse;
	}

	/**
	 * gets the tasks by the username
	 * 
	 * @param username
	 * @return
	 * @throws ParseException
	 */
	public List<TaskResponse> getCompleteTasks(String username, DateTimeRangeRequest dateTimeRange)
			throws ParseException {

		return getTasks(false, username, dateTimeRange);

	}

	/**
	 * gets the tasks by the username
	 * 
	 * @param username
	 * @return
	 * @throws ParseException
	 */
	public List<TaskResponse> getOpenTasks(String username, DateTimeRangeRequest dateTimeRange) throws ParseException {

		return getTasks(true, username, dateTimeRange);

	}

	/**
	 * Get the Tasks either open or completed where startdate and enddate are the
	 * same. or for one specific day.
	 * 
	 * @param open
	 * @param username
	 * @param dateTimeRange
	 * @return
	 * @throws ParseException
	 */
	public List<TaskResponse> getTasks(Boolean open, String username, DateTimeRangeRequest dateTimeRange)
			throws ParseException {
		
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");

		Date startDate = new SimpleDateFormat("yyyy-MM-dd").parse(dateTimeRange.getStartdate());
		Date endDate = new SimpleDateFormat("yyyy-MM-dd").parse(dateTimeRange.getEnddate());

		List<Task> taskList = taskRepository.findTaskByUserDate(username, startDate, endDate);

		List<Upload> uploads;
		List<TaskStatus> tasksStatus;
		List<TaskResponse> tasksResponse = new ArrayList<TaskResponse>();
		TaskResponse taskResponse;

		for (Task task : taskList) {
			System.out.println(task.getName() + " " + task.getStartdate() + " " + task.getEnddate());

			uploads = uploadRepository.findBySourceTypeAndId("task", task.getId());

			tasksStatus = taskStatusRepository.findTaskStatusByTaskId(task.getId(), startDate, endDate);

			taskResponse = new TaskResponse(task);

			taskResponse.setUploads(uploads);
			taskResponse.setTaskStatus(tasksStatus);

			taskResponse.setDueDate(sdf.format(startDate));

			// if getting open task and completed not found
			if (tasksStatus.isEmpty() && open) {
				taskResponse.setCompleted(false);
				tasksResponse.add(taskResponse);
			}

			// if getting completed task and completed task found
			if (!tasksStatus.isEmpty() && !open) {
				taskResponse.setCompleted(true);
				tasksResponse.add(taskResponse);
			}
		}

		return tasksResponse;
	}

	/**
	 * gets the tasks by the username
	 * 
	 * @param username
	 * @return
	 * @throws ParseException
	 */
	public List<TaskResponse> getTasksbyUsernamebydate(String username, DateTimeRangeRequest dateTimeRange)
			throws ParseException {
		
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");

		Date startDate = new SimpleDateFormat("yyyy-MM-dd").parse(dateTimeRange.getStartdate());
		Date endDate = new SimpleDateFormat("yyyy-MM-dd").parse(dateTimeRange.getEnddate());

		List<Task> taskList = taskRepository.findTaskByUserDate(username, startDate, endDate);

		List<Upload> uploads;
		List<TaskResponse> tasksResponse = new ArrayList<TaskResponse>();
		TaskResponse taskResponse;

		for (Task task : taskList) {
			System.out.println(task.getName() + " " + task.getStartdate() + " " + task.getEnddate());

			uploads = uploadRepository.findBySourceTypeAndId("task", task.getId());

			taskResponse = new TaskResponse(task);

			taskResponse.setUploads(uploads);

			taskResponse.setDueDate(sdf.format(startDate));
			tasksResponse.add(taskResponse);
		}

		return tasksResponse;
	}

	/**
	 * create tasks for a user, create the UserTask link and the uploads
	 * 
	 * @param rewardRequests
	 * @return
	 * @throws ParseException
	 */
	public boolean createTasksForUsername(List<TaskRequest> taskRequests, User user) throws ParseException {

		Task taskToSave;
		UserTask userTaskToSave;
		Upload upload;

		for (TaskRequest taskRequest : taskRequests) {

			// Save The Task
			taskRequest.setUpdateDateTime();
			taskRequest.setCreateDateTime();

			taskToSave = new Task(taskRequest);
			taskToSave.setUsername(user.getUsername());

			System.out.println(taskToSave.getStartdate());
			// makes sure it is a new Task
			taskToSave.setId(null);

			taskRepository.save(taskToSave);

			// Save the UserTask
			userTaskToSave = new UserTask();

			userTaskToSave.setId(null);
			userTaskToSave.setUsername(user.getUsername());
			userTaskToSave.setTask_id(taskToSave.getId());

			userTaskRepository.save(userTaskToSave);

			// save the uploads associated with a task
			for (int i = 0; i < taskRequest.getUploads().size(); i++) {
				upload = new Upload();
				upload = taskRequest.getUploads().get(i);
				upload.setId(null);
				upload.setCreateDateTime();
				upload.setUpdateDateTime();
				upload.setUploadable_type("task");
				upload.setUploadable_id(taskToSave.getId());

				uploadRepository.save(upload);
			}
		}

		return true;
	}

	/**
	 * create tasks for a user, create the UserTask link and the uploads
	 * 
	 * @param rewardRequests
	 * @return
	 * @throws ParseException
	 */
	public boolean updateTasksForUsername(List<TaskRequest> taskRequests, User user) throws ParseException {

		Task taskToSave;
		Upload upload;

		Date tempStartDate, tempEndDate;

		for (TaskRequest taskRequest : taskRequests) {

			tempStartDate = new SimpleDateFormat("yyyy-MM-dd").parse(taskRequest.getStrStartDate());
			tempEndDate = new SimpleDateFormat("yyyy-MM-dd").parse(taskRequest.getStrEndDate());

			taskRequest.setStartdate(new java.sql.Date(tempStartDate.getTime()));
			taskRequest.setEnddate(new java.sql.Date(tempEndDate.getTime()));
			// Save The Task
			taskRequest.setUpdateDateTime();
			taskRequest.setCreateDateTime();

			taskToSave = new Task(taskRequest);

			taskRepository.save(taskToSave);

			// save the uploads associated with a task
			for (int i = 0; i < taskRequest.getUploads().size(); i++) {
				upload = new Upload();
				upload = taskRequest.getUploads().get(i);
				upload.setCreateDateTime();
				upload.setUpdateDateTime();
				upload.setUploadable_type("task");
				upload.setUploadable_id(taskToSave.getId());

				uploadRepository.save(upload);
			}
		}

		return true;
	}

	/**
	 * update task and uploads associated with a task for a user.
	 * 
	 * @param rewardRequests
	 * @return
	 */
	public boolean updateTasksForUsername(List<TaskRequest> taskRequests, String username) {

		Task taskToSave;
		Upload upload;

		for (TaskRequest taskRequest : taskRequests) {

			// Update The Task
			taskRequest.setUpdateDateTime();

			taskToSave = new Task(taskRequest);
			taskRepository.save(taskToSave);

			// save the uploads associated with a task
			for (int i = 0; i < taskRequest.getUploads().size(); i++) {
				upload = new Upload();
				upload = taskRequest.getUploads().get(i);
				upload.setCreateDateTime();
				upload.setUpdateDateTime();
				upload.setUploadable_type("task");
				upload.setUploadable_id(taskToSave.getId());

				uploadRepository.save(upload);
			}
		}

		return true;
	}

	public boolean deleteTask(String username, Long task_id) throws ParseException {

		Task task = taskRepository.findTaskById(task_id);

		if (!task.getUsername().equals(username)) {
			return false;
		}

		List<TaskStatus> taskStatuses = taskStatusRepository.findByTaskId(task_id);

		if (!taskStatuses.isEmpty()) {
			for (TaskStatus taskStatus : taskStatuses) {
				taskStatusRepository.delete(taskStatus);
			}
		}

		List<UserTask> userTasks = userTaskRepository.findByTaskId(task_id);

		if (!userTasks.isEmpty()) {
			for (UserTask userTask : userTasks) {
				userTaskRepository.delete(userTask);
			}
		}

		taskRepository.delete(task);

		return true;
	}

}
