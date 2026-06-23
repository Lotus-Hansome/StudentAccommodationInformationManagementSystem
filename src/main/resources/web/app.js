const state = {
  token: "",
  role: "",
  username: "",
  studentId: "",
  students: [],
  studentTotal: 0,
  studentPage: 1,
  studentPageSize: 10,
  studentMode: "all",
  studentParams: {},
  requests: [],
  requestTotal: 0,
  requestPage: 1,
  requestPageSize: 10,
  requestMode: "pending",
  requestParams: {},
  users: [],
  buildings: [],
  rooms: [],
  logs: [],
  auditTotal: 0,
  auditPage: 1,
  auditPageSize: 20,
  auditKeyword: "",
};

const pageSizeOptions = [10, 20, 50];
const auditPageSizeOptions = [20, 50, 100];

const $ = (selector) => document.querySelector(selector);
const $$ = (selector) => Array.from(document.querySelectorAll(selector));

const titles = {
  dashboard: ["数据看板", "实时掌握宿舍入住和调换申请状态"],
  students: ["住宿信息", "维护学生住宿台账，支持查询、排序、分页、添加、修改和删除"],
  requests: ["调换申请", "提交、查看、撤回与审核宿舍调换申请"],
  analytics: ["智能分析", "按楼栋或宿舍生成运营评估与建议"],
  occupancy: ["床位明细", "查询每栋楼和每个宿舍的入住、容量与空余床位"],
  infrastructure: ["基础数据", "维护楼栋、宿舍、床位容量、类型和启停状态"],
  users: ["账号管理", "维护管理员和学生账号，支持禁用、绑定学号与密码重置"],
  audit: ["操作日志", "查看关键业务操作的审计记录"],
  settings: ["系统设置", "配置智能分析模型服务和运行参数"],
};

const adminViews = ["analytics", "occupancy", "infrastructure", "users", "audit", "settings"];

document.addEventListener("DOMContentLoaded", () => {
  $("#loginForm").addEventListener("submit", login);
  $$("[data-logout]").forEach((button) => button.addEventListener("click", logout));
  $$(".nav button").forEach((button) => button.addEventListener("click", () => navigate(button.dataset.view)));
  $("#passwordButton").addEventListener("click", openPasswordDialog);
  $("#closePasswordDialog").addEventListener("click", closePasswordDialog);
  $("#passwordForm").addEventListener("submit", changePassword);

  $("#allStudentsButton").addEventListener("click", showAllStudents);
  $("#sortStudentsButton").addEventListener("click", () => loadStudents("sorted", {}, 1));
  $("#searchStudentButton").addEventListener("click", searchStudentById);
  $("#searchDormButton").addEventListener("click", searchDormNumber);
  $("#advancedStudentSearchButton").addEventListener("click", searchStudentsAdvanced);
  $("#studentForm").addEventListener("submit", saveStudent);
  $("#resetStudentForm").addEventListener("click", resetStudentForm);

  $("#requestForm").addEventListener("submit", submitRequest);
  $("#pendingRequestsButton").addEventListener("click", () => loadRequests("pending", {}, 1));
  $("#allRequestsButton").addEventListener("click", () => loadRequests("all", {}, 1));
  $("#myRequestsButton").addEventListener("click", loadStudentRequests);

  $("#analyzeButton").addEventListener("click", analyzeDorm);
  $("#occupancyScope").addEventListener("change", updateOccupancyPlaceholder);
  $("#loadOccupancyButton").addEventListener("click", searchOccupancyDetails);
  $("#allOccupancyButton").addEventListener("click", showAllOccupancyDetails);

  $("#searchBuildingsButton").addEventListener("click", loadBuildings);
  $("#buildingForm").addEventListener("submit", saveBuilding);
  $("#searchRoomsButton").addEventListener("click", loadRooms);
  $("#roomForm").addEventListener("submit", saveRoom);

  $("#reloadUsersButton").addEventListener("click", loadUsers);
  $("#userForm").addEventListener("submit", saveUser);
  $("#resetUserFormButton").addEventListener("click", resetUserForm);

  $("#searchAuditButton").addEventListener("click", () => loadAuditLogs(1));
  $("#modelConfigForm").addEventListener("submit", saveModelConfig);
  $("#reloadModelConfigButton").addEventListener("click", loadModelConfig);
});

async function login(event) {
  event.preventDefault();
  const data = await api("/api/login", { method: "POST", body: new FormData(event.currentTarget) });
  state.token = data.token;
  state.role = data.role;
  state.username = data.username;
  state.studentId = data.studentId || "";
  $("#loginPage").classList.add("hidden");
  $("#appShell").classList.remove("hidden");
  $("#currentRole").textContent = data.role === "ADMIN" ? "系统管理员" : "普通用户";
  $("#currentUser").textContent = data.username;
  applyRole();
  navigate("dashboard");
  toast("登录成功");
}

async function logout() {
  const token = state.token;
  if (token) {
    try {
      await fetch("/api/logout", { method: "POST", headers: { "X-Auth-Token": token } });
    } catch (error) {
      console.warn("Logout request failed", error);
    }
  }
  Object.assign(state, {
    token: "",
    role: "",
    username: "",
    studentId: "",
    students: [],
    requests: [],
    users: [],
    buildings: [],
    rooms: [],
    logs: [],
  });
  $("#appShell").classList.add("hidden");
  $("#loginPage").classList.remove("hidden");
  $("#currentRole").textContent = "";
  $("#currentUser").textContent = "";
  $("#loginForm").reset();
  closePasswordDialog();
  toast("已退出登录");
}

function applyRole() {
  const isAdmin = state.role === "ADMIN";
  $$("[data-admin-only]").forEach((node) => node.classList.toggle("hidden", !isAdmin));
  $("#requestSubmitPanel").classList.toggle("hidden", isAdmin);
  const requestForm = $("#requestForm");
  const requestStudentQuery = $("#requestStudentQuery");
  if (!isAdmin && state.studentId) {
    requestForm.studentId.value = state.studentId;
    requestForm.studentId.readOnly = true;
    requestStudentQuery.value = state.studentId;
    requestStudentQuery.readOnly = true;
  } else {
    requestForm.studentId.readOnly = false;
    requestStudentQuery.readOnly = false;
  }
  const active = $(".nav button.active");
  if (!isAdmin && active && adminViews.includes(active.dataset.view)) {
    navigate("dashboard");
  }
}

function navigate(view) {
  if (adminViews.includes(view) && state.role !== "ADMIN") return;
  $$(".nav button").forEach((button) => button.classList.toggle("active", button.dataset.view === view));
  $$(".view").forEach((section) => section.classList.toggle("active", section.id === `${view}View`));
  $("#pageTitle").textContent = titles[view][0];
  $("#pageSubtitle").textContent = titles[view][1];
  if (view === "dashboard") loadOverview();
  if (view === "students") loadStudents("all", {}, 1);
  if (view === "requests") {
    loadRequests(state.role === "ADMIN" ? "pending" : "student", state.role === "ADMIN" ? {} : { studentId: state.studentId }, 1);
  }
  if (view === "occupancy") {
    updateOccupancyPlaceholder();
    loadOccupancyDetails($("#occupancyScope").value, $("#occupancyQuery").value.trim());
  }
  if (view === "infrastructure") loadInfrastructure();
  if (view === "users") loadUsers();
  if (view === "audit") loadAuditLogs(1);
  if (view === "settings") loadModelConfig();
}

async function loadOverview() {
  const data = await api("/api/overview");
  $("#metricStudents").textContent = data.totalStudents;
  $("#metricRooms").textContent = data.roomCount;
  $("#metricVacant").textContent = data.vacantBeds;
  $("#metricPending").textContent = data.pendingRequests;
  $("#occupancyText").textContent = `${data.occupancyRate}%`;
  $("#occupancyBar").style.width = `${Math.min(data.occupancyRate, 100)}%`;
  renderDepartmentBars(data.departments, "#departmentBars");
}

async function loadStudents(mode, params = {}, page = 1) {
  state.studentMode = mode;
  state.studentParams = { ...params };
  state.studentPage = page;
  const query = new URLSearchParams({ mode, ...params, page, pageSize: state.studentPageSize });
  const data = await api(`/api/students?${query.toString()}`);
  state.students = data.students;
  state.studentTotal = data.total ?? data.students.length;
  state.studentPage = data.page ?? page;
  state.studentPageSize = data.pageSize ?? state.studentPageSize;
  renderStudents();
}

function reloadStudents() {
  return loadStudents(state.studentMode, state.studentParams, state.studentPage);
}

function showAllStudents() {
  ["studentIdQuery", "dormQuery", "studentBuildingQuery", "departmentQuery", "classQuery", "studentKeywordQuery"].forEach((id) => {
    $(`#${id}`).value = "";
  });
  return loadStudents("all", {}, 1);
}

function searchStudentById() {
  const studentId = $("#studentIdQuery").value.trim();
  if (!studentId) return toast("请输入学号");
  loadStudents("student", { studentId }, 1);
}

function searchDormNumber() {
  const dormNumber = $("#dormQuery").value.trim();
  if (!dormNumber) return toast("请输入宿舍号");
  loadStudents("dorm", { dormNumber }, 1);
}

function searchStudentsAdvanced() {
  const params = {
    buildingNumber: $("#studentBuildingQuery").value.trim(),
    department: $("#departmentQuery").value.trim(),
    className: $("#classQuery").value.trim(),
    keyword: $("#studentKeywordQuery").value.trim(),
  };
  if (!Object.values(params).some(Boolean)) return toast("请输入至少一个组合查询条件");
  loadStudents("advanced", params, 1);
}

function renderStudents() {
  const tbody = $("#studentsTable");
  if (!state.students.length) {
    tbody.innerHTML = `<tr><td colspan="${studentColumnCount()}">暂无数据</td></tr>`;
    renderPagination("studentsPagination", "student", state.studentTotal, state.studentPage, state.studentPageSize);
    return;
  }
  tbody.innerHTML = state.students.map((student) => `
    <tr>
      <td>${escapeHtml(student.studentId)}</td>
      <td>${escapeHtml(student.name)}</td>
      <td>${escapeHtml(student.department)}</td>
      <td>${escapeHtml(student.className)}</td>
      <td>${escapeHtml(student.dormNumber)}</td>
      <td>${escapeHtml(student.dormPhone)}</td>
      <td>${escapeHtml(student.bedNumber)}</td>
      ${state.role === "ADMIN" ? `<td><div class="row-actions">
        <button class="btn" onclick="editStudent('${escapeJs(student.studentId)}')"><svg><use href="#icon-edit"></use></svg>编辑</button>
        <button class="btn danger" onclick="deleteStudent('${escapeJs(student.studentId)}','${escapeJs(student.dormNumber)}')"><svg><use href="#icon-trash"></use></svg>删除</button>
      </div></td>` : ""}
    </tr>
  `).join("");
  renderPagination("studentsPagination", "student", state.studentTotal, state.studentPage, state.studentPageSize);
}

function editStudent(studentId) {
  const student = state.students.find((item) => item.studentId === studentId);
  if (!student) return;
  const form = $("#studentForm");
  form.mode.value = "edit";
  form.studentId.value = student.studentId;
  form.studentId.readOnly = true;
  form.name.value = student.name;
  form.department.value = student.department;
  form.className.value = student.className;
  form.dormNumber.value = student.dormNumber;
  form.dormPhone.value = student.dormPhone;
  form.bedNumber.value = student.bedNumber;
  $("#studentFormTitle").textContent = "编辑住宿信息";
}

async function saveStudent(event) {
  event.preventDefault();
  const form = event.currentTarget;
  const method = form.mode.value === "edit" ? "PUT" : "POST";
  await api("/api/students", { method, body: new FormData(form) });
  resetStudentForm();
  await reloadStudents();
  await loadOverview();
  toast("住宿信息已保存");
}

function resetStudentForm() {
  const form = $("#studentForm");
  form.reset();
  form.mode.value = "create";
  form.studentId.readOnly = false;
  $("#studentFormTitle").textContent = "新增住宿信息";
}

async function deleteStudent(studentId, dormNumber) {
  if (!confirm(`确认删除学号 ${studentId} 在宿舍 ${dormNumber} 的记录吗？`)) return;
  const query = new URLSearchParams({ studentId, dormNumber });
  await api(`/api/students?${query.toString()}`, { method: "DELETE" });
  await reloadStudents();
  await loadOverview();
  toast("记录已删除");
}

async function submitRequest(event) {
  event.preventDefault();
  const form = event.currentTarget;
  const result = await api("/api/requests", { method: "POST", body: new FormData(form) });
  $("#requestStudentQuery").value = state.role === "ADMIN" ? form.studentId.value : state.studentId;
  form.reset();
  if (state.role !== "ADMIN" && state.studentId) form.studentId.value = state.studentId;
  await loadRequests("student", { studentId: $("#requestStudentQuery").value.trim() }, 1);
  await loadOverview();
  toast(`申请已提交：${result.id}`);
}

function loadStudentRequests() {
  const studentId = state.role === "ADMIN" ? $("#requestStudentQuery").value.trim() : state.studentId;
  if (!studentId) return toast("请输入学号");
  loadRequests("student", { studentId }, 1);
}

async function loadRequests(mode, params = {}, page = 1) {
  state.requestMode = mode;
  state.requestParams = { ...params };
  state.requestPage = page;
  if (mode === "student" && !params.studentId) {
    state.requests = [];
    state.requestTotal = 0;
    renderRequestsMessage("请输入学号查看申请记录");
    return;
  }
  const query = new URLSearchParams({ mode, ...params, page, pageSize: state.requestPageSize });
  const data = await api(`/api/requests?${query.toString()}`);
  state.requests = data.requests;
  state.requestTotal = data.total ?? data.requests.length;
  state.requestPage = data.page ?? page;
  state.requestPageSize = data.pageSize ?? state.requestPageSize;
  renderRequests();
}

function renderRequests() {
  const tbody = $("#requestsTable");
  if (!state.requests.length) {
    tbody.innerHTML = `<tr><td colspan="${requestColumnCount()}">暂无申请</td></tr>`;
    renderPagination("requestsPagination", "request", state.requestTotal, state.requestPage, state.requestPageSize);
    return;
  }
  tbody.innerHTML = state.requests.map((request) => `
    <tr>
      <td>${escapeHtml(request.id)}</td>
      <td>${escapeHtml(request.studentId)}</td>
      <td>${escapeHtml(request.currentDormNumber)} / ${escapeHtml(request.currentBedNumber)}</td>
      <td>${escapeHtml(request.targetDormNumber)} / ${escapeHtml(request.targetBedNumber)}</td>
      <td>${statusBadge(request.status, request.statusText)}</td>
      <td>${escapeHtml(request.createdAt)}</td>
      <td>${escapeHtml(request.handledAt || "")}</td>
      <td>${escapeHtml(request.reason)}${request.adminComment ? ` / ${escapeHtml(request.adminComment)}` : ""}</td>
      ${state.role === "ADMIN" ? `<td>${request.status === "PENDING" ? `<div class="row-actions">
        <button class="btn success" onclick="decideRequest('${escapeJs(request.id)}', true)"><svg><use href="#icon-check"></use></svg>同意</button>
        <button class="btn danger" onclick="decideRequest('${escapeJs(request.id)}', false)"><svg><use href="#icon-x"></use></svg>拒绝</button>
      </div>` : ""}</td>` : `<td>${request.status === "PENDING" ? `<button class="btn danger" onclick="cancelRequest('${escapeJs(request.id)}')"><svg><use href="#icon-x"></use></svg>撤回</button>` : ""}</td>`}
    </tr>
  `).join("");
  renderPagination("requestsPagination", "request", state.requestTotal, state.requestPage, state.requestPageSize);
}

function renderRequestsMessage(message) {
  $("#requestsTable").innerHTML = `<tr><td colspan="${requestColumnCount()}">${escapeHtml(message)}</td></tr>`;
  renderPagination("requestsPagination", "request", 0, state.requestPage, state.requestPageSize);
}

async function decideRequest(requestId, approved) {
  const comment = prompt("请输入审批意见：", approved ? "同意调换" : "");
  if (comment === null) return;
  if (!comment.trim()) return toast("审批意见不能为空");
  const body = new FormData();
  body.set("requestId", requestId);
  body.set("comment", comment.trim());
  await api(approved ? "/api/requests/approve" : "/api/requests/reject", { method: "POST", body });
  await loadRequests(state.requestMode, state.requestParams, state.requestPage);
  await loadOverview();
  toast("审批已完成");
}

async function cancelRequest(requestId) {
  if (!confirm("确认撤回该调换申请吗？")) return;
  const body = new FormData();
  body.set("requestId", requestId);
  await api("/api/requests/cancel", { method: "POST", body });
  await loadRequests(state.requestMode, state.requestParams, state.requestPage);
  await loadOverview();
  toast("申请已撤回");
}

async function analyzeDorm() {
  const scope = $("#analysisScope").value;
  const value = $("#analysisValue").value.trim();
  if (!value) return toast("请输入楼栋号或宿舍号");
  $("#analysisText").textContent = "正在生成建议...";
  const query = new URLSearchParams({ scope, value });
  const data = await api(`/api/statistics?${query.toString()}`);
  const statistics = data.statistics;
  $("#analysisStudents").textContent = statistics.totalStudents;
  $("#analysisCapacity").textContent = statistics.totalCapacity;
  $("#analysisVacant").textContent = statistics.vacantBeds;
  $("#statisticsText").textContent = statistics.promptText;
  $("#analysisText").textContent = data.analysis;
}

async function loadOccupancyDetails(scope, value = "") {
  const query = new URLSearchParams({ scope, value });
  const data = await api(`/api/occupancy?${query.toString()}`);
  renderOccupancyDetails(data.items);
}

function searchOccupancyDetails() {
  const scope = $("#occupancyScope").value;
  const value = $("#occupancyQuery").value.trim();
  if (!value) return toast(scope === "dorms" ? "请输入宿舍号" : "请输入楼栋号");
  loadOccupancyDetails(scope, value);
}

function showAllOccupancyDetails() {
  $("#occupancyQuery").value = "";
  loadOccupancyDetails($("#occupancyScope").value);
}

function updateOccupancyPlaceholder() {
  $("#occupancyQuery").placeholder = $("#occupancyScope").value === "dorms" ? "宿舍号" : "楼栋号";
}

function renderOccupancyDetails(items) {
  const tbody = $("#occupancyTable");
  if (!items.length) {
    tbody.innerHTML = `<tr><td colspan="6">暂无床位数据</td></tr>`;
    return;
  }
  tbody.innerHTML = items.map((item) => `
    <tr>
      <td>${escapeHtml(item.scope)}</td>
      <td>${item.roomCount}</td>
      <td>${item.totalStudents}</td>
      <td>${item.totalCapacity}</td>
      <td>${item.vacantBeds}</td>
      <td>${item.occupancyRate}%</td>
    </tr>
  `).join("");
}

async function loadInfrastructure() {
  await Promise.all([loadBuildings(), loadRooms()]);
}

async function loadBuildings() {
  const query = new URLSearchParams({ keyword: $("#buildingKeyword").value.trim() });
  const data = await api(`/api/buildings?${query.toString()}`);
  state.buildings = data.buildings;
  renderBuildings();
}

function renderBuildings() {
  const tbody = $("#buildingsTable");
  if (!state.buildings.length) {
    tbody.innerHTML = `<tr><td colspan="6">暂无楼栋</td></tr>`;
    return;
  }
  tbody.innerHTML = state.buildings.map((building) => `
    <tr>
      <td>${escapeHtml(building.buildingNumber)}</td>
      <td>${escapeHtml(building.buildingName)}</td>
      <td>${genderText(building.genderType)}</td>
      <td>${building.totalFloors}</td>
      <td>${statusText(building.status)}</td>
      <td><button class="btn" onclick="editBuilding('${escapeJs(building.buildingNumber)}')"><svg><use href="#icon-edit"></use></svg>编辑</button></td>
    </tr>
  `).join("");
}

function editBuilding(buildingNumber) {
  const building = state.buildings.find((item) => item.buildingNumber === buildingNumber);
  if (!building) return;
  const form = $("#buildingForm");
  form.buildingNumber.value = building.buildingNumber;
  form.buildingName.value = building.buildingName;
  form.genderType.value = building.genderType;
  form.totalFloors.value = building.totalFloors;
  form.status.value = building.status;
}

async function saveBuilding(event) {
  event.preventDefault();
  await api("/api/buildings", { method: "POST", body: new FormData(event.currentTarget) });
  event.currentTarget.reset();
  event.currentTarget.totalFloors.value = 6;
  await loadBuildings();
  toast("楼栋已保存");
}

async function loadRooms() {
  const query = new URLSearchParams({
    buildingNumber: $("#roomBuildingQuery").value.trim(),
    keyword: $("#roomKeyword").value.trim(),
  });
  const data = await api(`/api/rooms?${query.toString()}`);
  state.rooms = data.rooms;
  renderRooms();
}

function renderRooms() {
  const tbody = $("#roomsTable");
  if (!state.rooms.length) {
    tbody.innerHTML = `<tr><td colspan="7">暂无宿舍</td></tr>`;
    return;
  }
  tbody.innerHTML = state.rooms.map((room) => `
    <tr>
      <td>${escapeHtml(room.dormNumber)}</td>
      <td>${escapeHtml(room.buildingNumber)}</td>
      <td>${room.floorNumber}</td>
      <td>${room.capacity}</td>
      <td>${escapeHtml(room.phone)}</td>
      <td>${statusText(room.status)}</td>
      <td><button class="btn" onclick="editRoom('${escapeJs(room.dormNumber)}')"><svg><use href="#icon-edit"></use></svg>编辑</button></td>
    </tr>
  `).join("");
}

function editRoom(dormNumber) {
  const room = state.rooms.find((item) => item.dormNumber === dormNumber);
  if (!room) return;
  const form = $("#roomForm");
  form.dormNumber.value = room.dormNumber;
  form.buildingNumber.value = room.buildingNumber;
  form.floorNumber.value = room.floorNumber;
  form.roomType.value = room.roomType;
  form.genderType.value = room.genderType;
  form.capacity.value = room.capacity;
  form.phone.value = room.phone;
  form.status.value = room.status;
}

async function saveRoom(event) {
  event.preventDefault();
  await api("/api/rooms", { method: "POST", body: new FormData(event.currentTarget) });
  event.currentTarget.reset();
  event.currentTarget.roomType.value = "标准四人间";
  event.currentTarget.capacity.value = 4;
  await loadRooms();
  toast("宿舍已保存");
}

async function loadUsers() {
  const data = await api("/api/users");
  state.users = data.users;
  renderUsers();
}

function renderUsers() {
  const tbody = $("#usersTable");
  if (!state.users.length) {
    tbody.innerHTML = `<tr><td colspan="5">暂无账号</td></tr>`;
    return;
  }
  tbody.innerHTML = state.users.map((user) => `
    <tr>
      <td>${escapeHtml(user.username)}</td>
      <td>${user.role === "ADMIN" ? "管理员" : "普通用户"}</td>
      <td>${escapeHtml(user.studentId || "")}</td>
      <td>${user.enabled ? statusText("ACTIVE") : statusText("DISABLED")}</td>
      <td><div class="row-actions">
        <button class="btn" onclick="editUser('${escapeJs(user.username)}')"><svg><use href="#icon-edit"></use></svg>编辑</button>
        <button class="btn" onclick="resetUserPassword('${escapeJs(user.username)}')"><svg><use href="#icon-key"></use></svg>重置密码</button>
      </div></td>
    </tr>
  `).join("");
}

function editUser(username) {
  const user = state.users.find((item) => item.username === username);
  if (!user) return;
  const form = $("#userForm");
  form.mode.value = "edit";
  form.username.value = user.username;
  form.username.readOnly = true;
  form.password.value = "";
  $("#userPasswordRow").classList.add("hidden");
  form.role.value = user.role;
  form.studentId.value = user.studentId || "";
  form.enabled.checked = user.enabled;
  $("#userFormTitle").textContent = "编辑账号";
}

function resetUserForm() {
  const form = $("#userForm");
  form.reset();
  form.mode.value = "create";
  form.username.readOnly = false;
  $("#userPasswordRow").classList.remove("hidden");
  form.enabled.checked = true;
  $("#userFormTitle").textContent = "新增账号";
}

async function saveUser(event) {
  event.preventDefault();
  const form = event.currentTarget;
  const method = form.mode.value === "edit" ? "PUT" : "POST";
  await api("/api/users", { method, body: new FormData(form) });
  resetUserForm();
  await loadUsers();
  toast("账号已保存");
}

async function resetUserPassword(username) {
  const newPassword = prompt(`请输入 ${username} 的新密码：`);
  if (newPassword === null) return;
  if (newPassword.trim().length < 6) return toast("新密码至少需要 6 位");
  const body = new FormData();
  body.set("username", username);
  body.set("newPassword", newPassword.trim());
  await api("/api/users/reset-password", { method: "POST", body });
  toast("密码已重置");
}

async function loadAuditLogs(page = 1) {
  state.auditKeyword = $("#auditKeyword").value.trim();
  state.auditPage = page;
  const query = new URLSearchParams({ keyword: state.auditKeyword, page, pageSize: state.auditPageSize });
  const data = await api(`/api/audit-logs?${query.toString()}`);
  state.logs = data.logs;
  state.auditTotal = data.total ?? data.logs.length;
  state.auditPage = data.page ?? page;
  state.auditPageSize = data.pageSize ?? state.auditPageSize;
  renderAuditLogs();
}

function renderAuditLogs() {
  const tbody = $("#auditTable");
  if (!state.logs.length) {
    tbody.innerHTML = `<tr><td colspan="6">暂无日志</td></tr>`;
    renderPagination("auditPagination", "audit", state.auditTotal, state.auditPage, state.auditPageSize);
    return;
  }
  tbody.innerHTML = state.logs.map((log) => `
    <tr>
      <td>${escapeHtml(log.createdAt)}</td>
      <td>${escapeHtml(log.operator)}</td>
      <td>${escapeHtml(log.action)}</td>
      <td>${escapeHtml(log.targetType)}</td>
      <td>${escapeHtml(log.targetId)}</td>
      <td>${escapeHtml(log.detail)}</td>
    </tr>
  `).join("");
  renderPagination("auditPagination", "audit", state.auditTotal, state.auditPage, state.auditPageSize);
}

async function loadModelConfig() {
  const data = await api("/api/model-config");
  const form = $("#modelConfigForm");
  form.apiUrl.value = data.apiUrl || "";
  form.model.value = data.model || "";
  form.apiKey.value = "";
  form.apiKey.placeholder = "";
  const status = $("#modelConfigStatus");
  status.textContent = data.configured ? `已配置 · ${data.sourceText}` : "未配置";
  status.className = `status ${data.configured ? "approved" : "pending"}`;
}

async function saveModelConfig(event) {
  event.preventDefault();
  const data = await api("/api/model-config", { method: "POST", body: new FormData(event.currentTarget) });
  event.currentTarget.apiKey.value = "";
  const status = $("#modelConfigStatus");
  status.textContent = data.configured ? `已配置 · ${data.sourceText}` : "未配置";
  status.className = `status ${data.configured ? "approved" : "pending"}`;
  toast(data.configured ? "模型服务配置已保存" : "配置已保存，但仍缺少接口地址、模型名或 API Key");
  await loadModelConfig();
}

function openPasswordDialog() {
  $("#passwordDialog").classList.remove("hidden");
  $("#passwordForm").reset();
}

function closePasswordDialog() {
  $("#passwordDialog").classList.add("hidden");
}

async function changePassword(event) {
  event.preventDefault();
  await api("/api/users/change-password", { method: "POST", body: new FormData(event.currentTarget) });
  closePasswordDialog();
  toast("密码已修改，请重新登录");
  await logout();
}

function renderDepartmentBars(departments, target) {
  const container = $(target);
  if (!departments.length) {
    container.innerHTML = `<div class="bar-row"><span>暂无数据</span></div>`;
    return;
  }
  container.innerHTML = departments.map((item) => `
    <div class="bar-row">
      <span>${escapeHtml(item.department)}</span>
      <div class="bar-track"><div class="bar-fill" style="width:${Math.min(item.ratio, 100)}%"></div></div>
      <strong>${item.ratio}%</strong>
    </div>
  `).join("");
}

function studentColumnCount() {
  return state.role === "ADMIN" ? 8 : 7;
}

function requestColumnCount() {
  return 9;
}

function renderPagination(targetId, type, totalItems, currentPage, pageSize) {
  const target = $(`#${targetId}`);
  if (!target) return;
  const totalPages = Math.max(1, Math.ceil(totalItems / pageSize));
  const safePage = Math.min(Math.max(currentPage, 1), totalPages);
  const firstItem = totalItems === 0 ? 0 : (safePage - 1) * pageSize + 1;
  const lastItem = Math.min(safePage * pageSize, totalItems);
  const disabledPrevious = safePage <= 1 ? "disabled" : "";
  const disabledNext = safePage >= totalPages ? "disabled" : "";
  const optionsSource = type === "audit" ? auditPageSizeOptions : pageSizeOptions;
  const options = optionsSource.map((size) =>
    `<option value="${size}" ${size === pageSize ? "selected" : ""}>${size} 条/页</option>`
  ).join("");
  target.innerHTML = `
    <div class="pagination-summary">共 ${totalItems} 条 · 显示 ${firstItem}-${lastItem}</div>
    <div class="pagination-controls">
      <button class="page-button" type="button" ${disabledPrevious} onclick="changePage('${type}', 1)">首页</button>
      <button class="page-button" type="button" ${disabledPrevious} onclick="changePage('${type}', ${safePage - 1})">上一页</button>
      <span class="page-index">第 ${safePage} / ${totalPages} 页</span>
      <button class="page-button" type="button" ${disabledNext} onclick="changePage('${type}', ${safePage + 1})">下一页</button>
      <button class="page-button" type="button" ${disabledNext} onclick="changePage('${type}', ${totalPages})">末页</button>
      <select aria-label="每页条数" onchange="changePageSize('${type}', this.value)">${options}</select>
    </div>
  `;
}

function changePage(type, page) {
  if (type === "student") return loadStudents(state.studentMode, state.studentParams, page);
  if (type === "request") return loadRequests(state.requestMode, state.requestParams, page);
  if (type === "audit") return loadAuditLogs(page);
}

function changePageSize(type, pageSize) {
  const size = Number(pageSize);
  if (type === "student" && pageSizeOptions.includes(size)) {
    state.studentPageSize = size;
    return loadStudents(state.studentMode, state.studentParams, 1);
  }
  if (type === "request" && pageSizeOptions.includes(size)) {
    state.requestPageSize = size;
    return loadRequests(state.requestMode, state.requestParams, 1);
  }
  if (type === "audit" && auditPageSizeOptions.includes(size)) {
    state.auditPageSize = size;
    return loadAuditLogs(1);
  }
}

function statusBadge(status, text) {
  const klass = status === "APPROVED" ? "approved" : status === "REJECTED" || status === "CANCELED" ? "rejected" : "pending";
  return `<span class="status ${klass}">${escapeHtml(text)}</span>`;
}

function statusText(status) {
  const active = status === "ACTIVE";
  return `<span class="status ${active ? "approved" : "rejected"}">${active ? "启用" : "停用"}</span>`;
}

function genderText(value) {
  if (value === "MALE") return "男生";
  if (value === "FEMALE") return "女生";
  return "混合";
}

async function api(path, options = {}) {
  const headers = options.headers || {};
  if (state.token) headers["X-Auth-Token"] = state.token;
  let body = options.body;
  if (body instanceof FormData) {
    body = new URLSearchParams(body);
    headers["Content-Type"] = "application/x-www-form-urlencoded;charset=UTF-8";
  }
  const response = await fetch(path, { ...options, headers, body });
  const data = await response.json();
  if (!response.ok || data.success === false) {
    toast(data.message || "操作失败");
    throw new Error(data.message || "操作失败");
  }
  return data;
}

let toastTimer = 0;
function toast(message) {
  const box = $("#toast");
  box.textContent = message;
  box.classList.remove("hidden");
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => box.classList.add("hidden"), 2800);
}

function escapeHtml(value) {
  return String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");
}

function escapeJs(value) {
  return String(value ?? "").replaceAll("\\", "\\\\").replaceAll("'", "\\'");
}
