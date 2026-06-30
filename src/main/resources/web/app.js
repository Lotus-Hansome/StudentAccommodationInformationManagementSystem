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
  repairs: [],
  repairTotal: 0,
  repairPage: 1,
  repairPageSize: 10,
  homeData: null,
  users: [],
  buildings: [],
  rooms: [],
  logs: [],
  auditTotal: 0,
  auditPage: 1,
  auditPageSize: 20,
  auditKeyword: "",
  modelConfig: null,
};

const pageSizeOptions = [10, 20, 50];
const auditPageSizeOptions = [20, 50, 100];

const $ = (selector) => document.querySelector(selector);
const $$ = (selector) => Array.from(document.querySelectorAll(selector));

const titles = {
  dashboard: ["数据看板", "实时掌握宿舍入住和调换申请状态"],
  studentHome: ["我的首页", "查看个人住宿、同宿舍成员和服务进度"],
  students: ["住宿信息", "维护学生住宿台账，支持查询、排序、分页、添加、修改和删除"],
  requestSubmit: ["调换申请", "提交宿舍调换申请和调换理由"],
  requests: ["申请记录", "查看、撤回与审核宿舍调换申请"],
  repairSubmit: ["报修反馈", "提交宿舍维修问题和处理诉求"],
  repairs: ["报修记录", "查看报修反馈处理进度并撤回未完成记录"],
  analytics: ["智能分析", "按楼栋或宿舍生成运营评估与建议"],
  occupancy: ["床位明细", "查询每栋楼和每个宿舍的入住、容量与空余床位"],
  buildings: ["楼栋管理", "维护楼栋名称、住宿类型、楼层和启停状态"],
  rooms: ["宿舍管理", "维护宿舍所属楼栋、楼层、房型、容量和启停状态"],
  users: ["账号管理", "维护管理员和学生账号，支持禁用、绑定学号与密码重置"],
  audit: ["操作日志", "查看关键业务操作的审计记录"],
  settings: ["系统设置", "配置智能分析模型服务和运行参数"],
};

const adminViews = ["dashboard", "students", "analytics", "occupancy", "buildings", "rooms", "users", "audit", "settings"];
const studentOnlyViews = ["studentHome", "requestSubmit", "repairSubmit"];

document.addEventListener("DOMContentLoaded", () => {
  $("#loginForm").addEventListener("submit", login);
  $$("[data-logout]").forEach((button) => button.addEventListener("click", logout));
  $$(".nav button").forEach((button) => button.addEventListener("click", () => navigate(button.dataset.view)));
  $("#passwordButton").addEventListener("click", openPasswordDialog);
  $("#closePasswordDialog").addEventListener("click", closePasswordDialog);
  $("#passwordForm").addEventListener("submit", changePassword);

  $("#allStudentsButton").addEventListener("click", showAllStudents);
  $("#sortStudentsButton").addEventListener("click", sortStudents);
  $("#searchStudentButton").addEventListener("click", searchStudentById);
  $("#searchDormButton").addEventListener("click", searchDormNumber);
  $("#advancedStudentSearchButton").addEventListener("click", searchStudentsAdvanced);
  $("#studentForm").addEventListener("submit", saveStudent);
  $("#resetStudentForm").addEventListener("click", () => {
    resetStudentForm();
    toast("住宿信息表单已重置");
  });

  $("#requestForm").addEventListener("submit", submitRequest);
  $("#pendingRequestsButton").addEventListener("click", loadPendingRequests);
  $("#allRequestsButton").addEventListener("click", loadAllRequests);
  $("#myRequestsButton").addEventListener("click", loadStudentRequests);
  $("#repairForm").addEventListener("submit", submitRepair);
  $("#reloadRepairsButton").addEventListener("click", refreshRepairs);

  $("#analyzeButton").addEventListener("click", analyzeDorm);
  $("#occupancyScope").addEventListener("change", updateOccupancyPlaceholder);
  $("#loadOccupancyButton").addEventListener("click", searchOccupancyDetails);
  $("#allOccupancyButton").addEventListener("click", showAllOccupancyDetails);

  $("#searchBuildingsButton").addEventListener("click", searchBuildings);
  $("#buildingForm").addEventListener("submit", saveBuilding);
  $("#searchRoomsButton").addEventListener("click", searchRooms);
  $("#roomForm").addEventListener("submit", saveRoom);

  $("#reloadUsersButton").addEventListener("click", refreshUsers);
  $("#userForm").addEventListener("submit", saveUser);
  $("#userForm [name='role']").addEventListener("change", updateUserRoleFields);
  $("#resetUserFormButton").addEventListener("click", () => {
    resetUserForm();
    toast("账号表单已重置");
  });

  $("#searchAuditButton").addEventListener("click", searchAuditLogs);
  $("#modelConfigForm").addEventListener("submit", saveModelConfig);
  $("#reloadModelConfigButton").addEventListener("click", refreshModelConfig);
  $("#clearModelConfigButton").addEventListener("click", clearModelConfig);
  $("#addModelConfigButton").addEventListener("click", () => {
    openModelConfigForm();
    toast("请填写新的模型配置");
  });
  $("#cancelModelEditButton").addEventListener("click", cancelModelEdit);
  $("#modelConfigList").addEventListener("click", handleModelConfigAction);
  updateUserRoleFields();
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
  navigate(data.role === "ADMIN" ? "dashboard" : "studentHome");
  toast("登录成功");
}

async function logout(options = {}) {
  const silent = options && options.silent === true;
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
    repairs: [],
    homeData: null,
  });
  $("#appShell").classList.add("hidden");
  $("#loginPage").classList.remove("hidden");
  $("#currentRole").textContent = "";
  $("#currentUser").textContent = "";
  $("#loginForm").reset();
  closePasswordDialog();
  if (!silent) toast("已退出登录");
}

function applyRole() {
  const isAdmin = state.role === "ADMIN";
  $$("[data-admin-only]").forEach((node) => node.classList.toggle("hidden", !isAdmin));
  $$("[data-student-only]").forEach((node) => node.classList.toggle("hidden", isAdmin));
  $("#requestSubmitPanel").classList.toggle("hidden", isAdmin);
  $("#repairSubmitPanel").classList.toggle("hidden", isAdmin);
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
    navigate("studentHome");
  }
  if (isAdmin && active && studentOnlyViews.includes(active.dataset.view)) {
    navigate("dashboard");
  }
}

function navigate(view) {
  if (adminViews.includes(view) && state.role !== "ADMIN") return;
  if (studentOnlyViews.includes(view) && state.role === "ADMIN") return;
  $$(".nav button").forEach((button) => button.classList.toggle("active", button.dataset.view === view));
  $$(".view").forEach((section) => section.classList.toggle("active", section.id === `${view}View`));
  $("#pageTitle").textContent = titles[view][0];
  $("#pageSubtitle").textContent = titles[view][1];
  if (view === "dashboard") loadOverview();
  if (view === "studentHome") loadStudentHome();
  if (view === "students") loadStudents("all", {}, 1);
  if (view === "requests") {
    loadRequests(state.role === "ADMIN" ? "pending" : "student", state.role === "ADMIN" ? {} : { studentId: state.studentId }, 1);
  }
  if (view === "repairs") loadRepairs(1);
  if (view === "occupancy") {
    updateOccupancyPlaceholder();
    loadOccupancyDetails($("#occupancyScope").value, $("#occupancyQuery").value.trim());
  }
  if (view === "buildings") loadBuildings();
  if (view === "rooms") loadRooms();
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

async function refreshOverviewForAdmin() {
  if (state.role === "ADMIN") {
    await loadOverview();
  }
}

async function loadStudentHome() {
  const data = await api("/api/student-home");
  state.homeData = data;
  renderStudentHome(data);
}

function renderStudentHome(data) {
  const student = data.student || {};
  $("#homeStudentName").textContent = student.name || "--";
  $("#homeStudentMeta").textContent = `${student.department || "--"} · ${student.className || "--"}`;
  $("#homeStudentId").textContent = student.studentId || "--";
  $("#homeDormNumber").textContent = student.dormNumber || "--";
  $("#homeDormBadge").textContent = student.bedNumber ? `${student.bedNumber}号床` : "--";
  $("#homeBuildingNumber").textContent = student.buildingNumber ? `${student.buildingNumber}号楼` : "--";
  $("#homeBedNumber").textContent = student.bedNumber || "--";
  $("#homeDormPhone").textContent = student.dormPhone || "--";
  renderRoommates(data.roommates || [], student.studentId || "");
  renderTimeline("homeRequestTimeline", data.requests || [], requestTimelineItem);
  renderTimeline("homeRepairTimeline", data.repairs || [], repairTimelineItem);
}

function renderRoommates(roommates, currentStudentId) {
  const target = $("#roommateList");
  if (!roommates.length) {
    target.innerHTML = `<div class="empty-note">暂无同宿舍成员信息</div>`;
    return;
  }
  target.innerHTML = roommates.map((item) => `
    <div class="roommate-item ${item.studentId === currentStudentId ? "current" : ""}">
      <div><strong>${escapeHtml(item.name)}</strong><span>${escapeHtml(item.department)} · ${escapeHtml(item.className)}</span></div>
      <em>${escapeHtml(item.bedNumber)}号床</em>
    </div>
  `).join("");
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

async function showAllStudents() {
  ["studentIdQuery", "dormQuery", "studentBuildingQuery", "departmentQuery", "classQuery", "studentKeywordQuery"].forEach((id) => {
    $(`#${id}`).value = "";
  });
  await loadStudents("all", {}, 1);
  toast("已显示全部住宿信息");
}

async function sortStudents() {
  await loadStudents("sorted", {}, 1);
  toast("已按系/班级排序");
}

async function searchStudentById() {
  const studentId = $("#studentIdQuery").value.trim();
  if (!studentId) return toast("请输入学号");
  await loadStudents("student", { studentId }, 1);
  toast("学号查询完成");
}

async function searchDormNumber() {
  const dormNumber = $("#dormQuery").value.trim();
  if (!dormNumber) return toast("请输入宿舍号");
  await loadStudents("dorm", { dormNumber }, 1);
  toast("宿舍查询完成");
}

async function searchStudentsAdvanced() {
  const params = {
    buildingNumber: $("#studentBuildingQuery").value.trim(),
    department: $("#departmentQuery").value.trim(),
    className: $("#classQuery").value.trim(),
    keyword: $("#studentKeywordQuery").value.trim(),
  };
  if (!Object.values(params).some(Boolean)) return toast("请输入至少一个组合查询条件");
  await loadStudents("advanced", params, 1);
  toast("组合查询完成");
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
  form.bedNumber.value = student.bedNumber;
  $("#studentFormTitle").textContent = "编辑住宿信息";
  toast("住宿信息已载入，可修改后保存");
}

async function saveStudent(event) {
  event.preventDefault();
  const form = event.currentTarget;
  const method = form.mode.value === "edit" ? "PUT" : "POST";
  await api("/api/students", { method, body: new FormData(form) });
  const message = method === "PUT" ? "住宿信息修改成功" : "住宿信息添加成功";
  resetStudentForm();
  await reloadStudents();
  await refreshOverviewForAdmin();
  toast(message);
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
  await refreshOverviewForAdmin();
  toast("记录已删除");
}

async function submitRequest(event) {
  event.preventDefault();
  const form = event.currentTarget;
  const result = await api("/api/requests", { method: "POST", body: new FormData(form) });
  $("#requestStudentQuery").value = state.role === "ADMIN" ? form.studentId.value : state.studentId;
  form.reset();
  if (state.role !== "ADMIN" && state.studentId) form.studentId.value = state.studentId;
  if (state.role !== "ADMIN") await loadStudentHome();
  await refreshOverviewForAdmin();
  navigate("requests");
  toast(`申请已提交：${result.id}`);
}

async function loadPendingRequests() {
  await loadRequests("pending", {}, 1);
  toast("待审核申请已加载");
}

async function loadAllRequests() {
  await loadRequests("all", {}, 1);
  toast("全部申请已加载");
}

async function loadStudentRequests() {
  const studentId = state.role === "ADMIN" ? $("#requestStudentQuery").value.trim() : state.studentId;
  if (!studentId) return toast("请输入学号");
  await loadRequests("student", { studentId }, 1);
  toast("申请记录查询完成");
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
    renderRequestTimeline();
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
  renderRequestTimeline();
  renderPagination("requestsPagination", "request", state.requestTotal, state.requestPage, state.requestPageSize);
}

function renderRequestsMessage(message) {
  $("#requestsTable").innerHTML = `<tr><td colspan="${requestColumnCount()}">${escapeHtml(message)}</td></tr>`;
  renderRequestTimeline();
  renderPagination("requestsPagination", "request", 0, state.requestPage, state.requestPageSize);
}

function renderRequestTimeline() {
  if (state.role !== "ADMIN") {
    renderTimeline("requestTimeline", state.requests, requestTimelineItem);
  }
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
  await refreshOverviewForAdmin();
  toast("审批已完成");
}

async function cancelRequest(requestId) {
  if (!confirm("确认撤回该调换申请吗？")) return;
  const body = new FormData();
  body.set("requestId", requestId);
  await api("/api/requests/cancel", { method: "POST", body });
  await loadRequests(state.requestMode, state.requestParams, state.requestPage);
  if (state.role !== "ADMIN") await loadStudentHome();
  await refreshOverviewForAdmin();
  toast("申请已撤回");
}

async function loadRepairs(page = 1) {
  state.repairPage = page;
  const query = new URLSearchParams({ page, pageSize: state.repairPageSize });
  const data = await api(`/api/repairs?${query.toString()}`);
  state.repairs = data.repairs;
  state.repairTotal = data.total ?? data.repairs.length;
  state.repairPage = data.page ?? page;
  state.repairPageSize = data.pageSize ?? state.repairPageSize;
  renderRepairs();
}

async function refreshRepairs() {
  await loadRepairs(1);
  toast("报修记录已刷新");
}

async function submitRepair(event) {
  event.preventDefault();
  const form = event.currentTarget;
  const result = await api("/api/repairs", { method: "POST", body: new FormData(form) });
  form.reset();
  toast(`报修已提交：${result.id}`);
  if (state.role !== "ADMIN") await loadStudentHome();
  navigate("repairs");
}

function renderRepairs() {
  const tbody = $("#repairsTable");
  if (!state.repairs.length) {
    tbody.innerHTML = `<tr><td colspan="${repairColumnCount()}">暂无报修反馈</td></tr>`;
    renderPagination("repairsPagination", "repair", state.repairTotal, state.repairPage, state.repairPageSize);
    return;
  }
  tbody.innerHTML = state.repairs.map((repair) => `
    <tr>
      <td>${escapeHtml(repair.id)}</td>
      <td>${escapeHtml(repair.studentId)}</td>
      <td>${escapeHtml(repair.dormNumber)}</td>
      <td>${escapeHtml(repair.category)}</td>
      <td>${statusBadge(repair.status, repair.statusText)}</td>
      <td>${escapeHtml(repair.createdAt)}</td>
      <td>${escapeHtml(repair.handledAt || "")}</td>
      <td>${escapeHtml(repair.description)}${repair.adminComment ? ` / ${escapeHtml(repair.adminComment)}` : ""}</td>
      <td>${repairActions(repair)}</td>
    </tr>
  `).join("");
  renderPagination("repairsPagination", "repair", state.repairTotal, state.repairPage, state.repairPageSize);
}

function repairActions(repair) {
  if (["DONE", "REJECTED", "CANCELED"].includes(repair.status)) return "";
  if (state.role === "ADMIN") {
    return `<div class="row-actions">
      <button class="btn" onclick="decideRepair('${escapeJs(repair.id)}','PROCESSING')">处理中</button>
      <button class="btn success" onclick="decideRepair('${escapeJs(repair.id)}','DONE')"><svg><use href="#icon-check"></use></svg>完成</button>
      <button class="btn danger" onclick="decideRepair('${escapeJs(repair.id)}','REJECTED')"><svg><use href="#icon-x"></use></svg>驳回</button>
    </div>`;
  }
  return `<div class="row-actions">
    <button class="btn danger" onclick="cancelRepair('${escapeJs(repair.id)}')"><svg><use href="#icon-x"></use></svg>撤回</button>
  </div>`;
}

async function decideRepair(repairId, status) {
  const defaultComment = status === "PROCESSING" ? "已安排维修人员处理" : status === "DONE" ? "已处理完成" : "";
  const comment = prompt("请输入处理反馈：", defaultComment);
  if (comment === null) return;
  if (!comment.trim()) return toast("处理反馈不能为空");
  const body = new FormData();
  body.set("id", repairId);
  body.set("status", status);
  body.set("comment", comment.trim());
  await api("/api/repairs/status", { method: "POST", body });
  await loadRepairs(state.repairPage);
  toast("报修状态已更新");
}

async function cancelRepair(repairId) {
  if (!confirm("确认撤回该报修反馈吗？")) return;
  const body = new FormData();
  body.set("id", repairId);
  await api("/api/repairs/cancel", { method: "POST", body });
  await loadRepairs(state.repairPage);
  if (state.role !== "ADMIN") await loadStudentHome();
  toast("报修反馈已撤回");
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
  toast("智能分析建议已生成");
}

async function loadOccupancyDetails(scope, value = "") {
  const query = new URLSearchParams({ scope, value });
  const data = await api(`/api/occupancy?${query.toString()}`);
  renderOccupancyDetails(data.items);
}

async function searchOccupancyDetails() {
  const scope = $("#occupancyScope").value;
  const value = $("#occupancyQuery").value.trim();
  if (!value) return toast(scope === "dorms" ? "请输入宿舍号" : "请输入楼栋号");
  await loadOccupancyDetails(scope, value);
  toast("床位明细查询完成");
}

async function showAllOccupancyDetails() {
  $("#occupancyQuery").value = "";
  await loadOccupancyDetails($("#occupancyScope").value);
  toast("全部床位明细已加载");
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

async function loadBuildings() {
  const query = new URLSearchParams({ keyword: $("#buildingKeyword").value.trim() });
  const data = await api(`/api/buildings?${query.toString()}`);
  state.buildings = data.buildings;
  renderBuildings();
}

async function searchBuildings() {
  await loadBuildings();
  toast("楼栋查询完成");
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
  form.dataset.editing = "true";
  toast("楼栋信息已载入，可修改后保存");
}

async function saveBuilding(event) {
  event.preventDefault();
  const form = event.currentTarget;
  const editing = form.dataset.editing === "true";
  await api("/api/buildings", { method: "POST", body: new FormData(form) });
  toast(editing ? "楼栋信息修改成功" : "楼栋信息保存成功");
  form.reset();
  form.dataset.editing = "";
  form.totalFloors.value = 6;
  await loadBuildings();
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

async function searchRooms() {
  await loadRooms();
  toast("宿舍查询完成");
}

function renderRooms() {
  const tbody = $("#roomsTable");
  if (!state.rooms.length) {
    tbody.innerHTML = `<tr><td colspan="10">暂无宿舍</td></tr>`;
    return;
  }
  tbody.innerHTML = state.rooms.map((room) => {
    const supportsBedAvailability = Array.isArray(room.vacantBedNumbers)
      && Number.isFinite(Number(room.vacantBeds));
    const vacantBeds = supportsBedAvailability ? Number(room.vacantBeds) : "--";
    const vacancyDetail = supportsBedAvailability
      ? (room.vacantBedNumbers.length
        ? room.vacantBedNumbers.map((bed) => `${escapeHtml(bed)}号`).join("、")
        : (room.status === "ACTIVE" ? "无空余" : "不可分配"))
      : "请重启服务后查看";
    return `
      <tr>
        <td>${escapeHtml(room.dormNumber)}</td>
        <td>${escapeHtml(room.buildingNumber)}</td>
        <td>${room.floorNumber}</td>
        <td>${room.capacity}</td>
        <td>${Number.isFinite(Number(room.occupiedBeds)) ? room.occupiedBeds : "--"}</td>
        <td><div class="bed-availability"><strong>${vacantBeds}${supportsBedAvailability ? " 个" : ""}</strong><span>${vacancyDetail}</span></div></td>
        <td>${Number.isFinite(Number(room.lockedBeds)) ? room.lockedBeds : "--"}</td>
        <td>${escapeHtml(room.phone)}</td>
        <td>${statusText(room.status)}</td>
        <td><button class="btn" onclick="editRoom('${escapeJs(room.dormNumber)}')"><svg><use href="#icon-edit"></use></svg>编辑</button></td>
      </tr>
    `;
  }).join("");
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
  form.dataset.editing = "true";
  toast("宿舍信息已载入，可修改后保存");
}

async function saveRoom(event) {
  event.preventDefault();
  const form = event.currentTarget;
  const editing = form.dataset.editing === "true";
  const result = await api("/api/rooms", { method: "POST", body: new FormData(form) });
  toast(result.message || (editing ? "宿舍信息修改成功" : "宿舍信息保存成功"));
  form.reset();
  form.dataset.editing = "";
  form.roomType.value = "标准四人间";
  form.capacity.value = 4;
  await loadRooms();
}

async function loadUsers() {
  const data = await api("/api/users");
  state.users = data.users;
  renderUsers();
}

async function refreshUsers() {
  await loadUsers();
  toast("账号列表已刷新");
}

function renderUsers() {
  const tbody = $("#usersTable");
  if (!state.users.length) {
    tbody.innerHTML = `<tr><td colspan="5">暂无账号</td></tr>`;
    return;
  }
  tbody.innerHTML = state.users.map((user) => `
    <tr>
      <td>${escapeHtml(user.role === "ADMIN" ? user.username : user.studentId)}</td>
      <td>${user.role === "ADMIN" ? "管理员" : "普通用户"}</td>
      <td>${user.role === "ADMIN" ? "--" : escapeHtml(user.studentId || "")}</td>
      <td>${user.enabled ? statusText("ACTIVE") : statusText("DISABLED")}</td>
      <td><div class="row-actions">
        <button class="btn" onclick="editUser('${escapeJs(user.username)}')"><svg><use href="#icon-edit"></use></svg>编辑</button>
        <button class="btn" onclick="resetUserPassword('${escapeJs(user.username)}','${escapeJs(user.role === "ADMIN" ? user.username : user.studentId)}')"><svg><use href="#icon-key"></use></svg>重置密码</button>
        ${user.username === state.username ? "" : `<button class="btn danger" onclick="deleteUser('${escapeJs(user.username)}','${escapeJs(user.role === "ADMIN" ? user.username : user.studentId)}')"><svg><use href="#icon-trash"></use></svg>删除</button>`}
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
  updateUserRoleFields();
  form.enabled.checked = user.enabled;
  $("#userFormTitle").textContent = "编辑账号";
  toast("账号信息已载入，可修改后保存");
}

function resetUserForm() {
  const form = $("#userForm");
  form.reset();
  form.mode.value = "create";
  form.username.readOnly = false;
  $("#userPasswordRow").classList.remove("hidden");
  form.enabled.checked = true;
  $("#userFormTitle").textContent = "新增账号";
  updateUserRoleFields();
}

function updateUserRoleFields() {
  const form = $("#userForm");
  const isAdmin = form.role.value === "ADMIN";
  const isEditing = form.mode.value === "edit";
  $("#userUsernameRow").classList.toggle("hidden", !isAdmin);
  $("#userStudentIdRow").classList.toggle("hidden", isAdmin);
  form.username.disabled = !isAdmin && !isEditing;
  form.username.required = isAdmin;
  form.studentId.disabled = isAdmin;
  form.studentId.required = !isAdmin;
  if (isAdmin) form.studentId.value = "";
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

async function deleteUser(username, loginId = username) {
  if (!confirm(`确认删除账号 ${loginId} 吗？删除后该账号将无法登录。`)) return;
  const query = new URLSearchParams({ username });
  await api(`/api/users?${query.toString()}`, { method: "DELETE" });
  const form = $("#userForm");
  if (form.mode.value === "edit" && form.username.value === username) {
    resetUserForm();
  }
  await loadUsers();
  toast("账号已删除");
}

async function resetUserPassword(username, loginId = username) {
  const newPassword = prompt(`请输入 ${loginId} 的新密码：`);
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

async function searchAuditLogs() {
  await loadAuditLogs(1);
  toast("操作日志查询完成");
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
  updateModelConfigStatus(data);
}

async function refreshModelConfig() {
  await loadModelConfig();
  toast("模型配置状态已刷新");
}

async function saveModelConfig(event) {
  event.preventDefault();
  const form = event.currentTarget;
  const body = new FormData(form);
  body.set("action", "save");
  const data = await api("/api/model-config", { method: "POST", body });
  form.reset();
  setModelEditorVisible(false);
  updateModelConfigStatus(data);
  toast("模型服务配置已保存，可在列表中选择启用");
}

async function clearModelConfig() {
  if (!confirm("确定清空全部模型服务配置吗？")) return;
  const data = await api("/api/model-config", { method: "DELETE" });
  updateModelConfigStatus(data);
  setModelEditorVisible(false);
  toast("模型服务配置已清空，当前使用本地规则分析");
}

async function handleModelConfigAction(event) {
  const button = event.target.closest("button[data-action]");
  if (!button) return;
  const action = button.dataset.action;
  const id = button.dataset.id || "";
  if (action === "select") {
    await selectModelConfig(id);
  } else if (action === "edit") {
    openModelConfigForm(modelConfigById(id));
    toast("模型配置已载入，可修改后保存");
  } else if (action === "copy") {
    await copyModelConfigUrl(modelConfigById(id));
  } else if (action === "delete") {
    await deleteModelConfig(id);
  }
}

async function selectModelConfig(id) {
  const body = new FormData();
  body.set("action", "select");
  body.set("id", id || "local");
  const data = await api("/api/model-config", { method: "POST", body });
  updateModelConfigStatus(data);
  toast(id === "local" ? "已切换为本地规则分析" : "模型配置已启用");
}

async function deleteModelConfig(id) {
  const profile = modelConfigById(id);
  if (!profile) return;
  if (!confirm(`确定删除配置 ${profile.name} 吗？`)) return;
  const query = new URLSearchParams({ id });
  const data = await api(`/api/model-config?${query.toString()}`, { method: "DELETE" });
  updateModelConfigStatus(data);
  toast("模型配置已删除");
}

function updateModelConfigStatus(data) {
  state.modelConfig = data;
  const status = $("#modelConfigStatus");
  status.textContent = data.localAnalysis ? "本地规则分析" : data.configured ? `已启用 · ${data.sourceText}` : "未启用模型";
  status.className = `status ${data.configured || data.localAnalysis ? "approved" : "pending"}`;
  const clearButton = $("#clearModelConfigButton");
  clearButton.disabled = !data.configs || data.configs.length === 0;
  clearButton.title = clearButton.disabled ? "当前没有模型配置" : "清空全部配置";
  renderModelConfigList(data);
}

function setModelEditorVisible(visible) {
  $("#modelConfigForm").classList.toggle("hidden", !visible);
}

function cancelModelEdit() {
  setModelEditorVisible(false);
  toast("模型配置编辑已取消");
}

function openModelConfigForm(profile = null) {
  const form = $("#modelConfigForm");
  form.reset();
  form.id.value = profile ? profile.id : "";
  form.name.value = profile ? profile.name : "";
  form.apiUrl.value = profile ? profile.apiUrl : "";
  form.model.value = profile ? profile.model : "";
  form.apiKey.placeholder = profile && profile.apiKeySet ? "已保存，留空则不修改" : "";
  setModelEditorVisible(true);
}

function renderModelConfigList(data) {
  const list = $("#modelConfigList");
  const localActive = !!data.localAnalysis;
  const localCard = modelConfigCardHtml({
    id: "local",
    name: "本地规则分析",
    apiUrl: "不调用外部大模型接口",
    model: "基于系统统计规则生成建议",
    apiKeySet: false,
    configured: true,
    active: localActive,
    local: true,
  });
  const configCards = (data.configs || []).map(modelConfigCardHtml).join("");
  list.innerHTML = localCard + configCards;
}

function modelConfigCardHtml(profile) {
  const provider = profile.local ? { name: profile.name, mark: "LOCAL" } : inferModelProvider(profile.apiUrl, profile.model, profile.name);
  const activeClass = profile.active ? " configured" : "";
  const disabledSelect = !profile.local && !profile.configured ? "disabled" : "";
  const selectText = profile.active ? "已启用" : profile.local ? "使用本地" : "启用";
  const meta = profile.local
    ? "无需 API Key"
    : `模型：${escapeHtml(profile.model || "未设置")} · API Key：${profile.apiKeySet ? "已保存" : "未设置"}`;
  const actions = profile.local ? `
      <button type="button" class="btn ${profile.active ? "" : "primary"} enable-btn" data-action="select" data-id="local" ${profile.active ? "disabled" : ""}><svg><use href="#icon-play"></use></svg>${selectText}</button>
    ` : `
      <button type="button" class="btn ${profile.active ? "" : "primary"} enable-btn" data-action="select" data-id="${escapeHtml(profile.id)}" ${profile.active || disabledSelect ? "disabled" : ""}><svg><use href="#icon-play"></use></svg>${selectText}</button>
      <button type="button" class="icon-btn" data-action="edit" data-id="${escapeHtml(profile.id)}" title="编辑配置"><svg><use href="#icon-edit"></use></svg></button>
      <button type="button" class="icon-btn" data-action="copy" data-id="${escapeHtml(profile.id)}" title="复制接口地址"><svg><use href="#icon-copy"></use></svg></button>
      <button type="button" class="icon-btn danger" data-action="delete" data-id="${escapeHtml(profile.id)}" title="删除配置"><svg><use href="#icon-trash"></use></svg></button>
    `;
  return `
    <article class="model-provider-card${activeClass}">
      <div class="model-provider-row">
        <div class="provider-grip" aria-hidden="true"></div>
        <div class="provider-logo">${escapeHtml(provider.mark)}</div>
        <div class="provider-main">
          <div class="provider-title-row">
            <h3>${escapeHtml(profile.name || provider.name)}</h3>
            <span class="status ${profile.active ? "approved" : profile.configured ? "pending" : "rejected"}">${profile.active ? "当前使用" : profile.configured ? "可启用" : "未完整"}</span>
          </div>
          <div class="provider-url">${escapeHtml(profile.apiUrl || "未填写接口地址")}</div>
          <div class="provider-meta"><span>${meta}</span></div>
        </div>
        <div class="provider-actions">${actions}</div>
      </div>
    </article>
  `;
}

function modelConfigById(id) {
  return ((state.modelConfig && state.modelConfig.configs) || []).find((profile) => profile.id === id);
}

async function copyModelConfigUrl(profile) {
  const url = profile ? profile.apiUrl : "";
  if (!url) return toast("暂无接口地址可复制");
  try {
    await navigator.clipboard.writeText(url);
    toast("接口地址已复制");
  } catch (error) {
    window.prompt("复制接口地址", url);
    toast("请在弹窗中复制接口地址");
  }
}

function inferModelProvider(apiUrl, model, fallbackName = "") {
  const text = `${apiUrl || ""} ${model || ""}`.toLowerCase();
  if (text.includes("deepseek")) return { name: "DeepSeek Compatible", mark: "DS" };
  if (text.includes("openai")) return { name: "OpenAI Official", mark: "AI" };
  if (text.includes("siliconflow")) return { name: "SiliconFlow", mark: "SF" };
  if (text.includes("dashscope") || text.includes("aliyuncs")) return { name: "DashScope Compatible", mark: "DB" };
  return { name: fallbackName || "OpenAI 兼容模型服务", mark: "LLM" };
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
  await logout({ silent: true });
  toast("密码已修改，请重新登录");
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

function repairColumnCount() {
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

async function changePage(type, page) {
  if (type === "student") {
    await loadStudents(state.studentMode, state.studentParams, page);
  } else if (type === "request") {
    await loadRequests(state.requestMode, state.requestParams, page);
  } else if (type === "repair") {
    await loadRepairs(page);
  } else if (type === "audit") {
    await loadAuditLogs(page);
  } else {
    return;
  }
  toast(`${paginationLabel(type)}已切换到第 ${page} 页`);
}

async function changePageSize(type, pageSize) {
  const size = Number(pageSize);
  if (type === "student" && pageSizeOptions.includes(size)) {
    state.studentPageSize = size;
    await loadStudents(state.studentMode, state.studentParams, 1);
  } else if (type === "request" && pageSizeOptions.includes(size)) {
    state.requestPageSize = size;
    await loadRequests(state.requestMode, state.requestParams, 1);
  } else if (type === "repair" && pageSizeOptions.includes(size)) {
    state.repairPageSize = size;
    await loadRepairs(1);
  } else if (type === "audit" && auditPageSizeOptions.includes(size)) {
    state.auditPageSize = size;
    await loadAuditLogs(1);
  } else {
    return;
  }
  toast(`${paginationLabel(type)}每页条数已更新为 ${size} 条`);
}

function paginationLabel(type) {
  if (type === "student") return "住宿信息";
  if (type === "request") return "申请记录";
  if (type === "repair") return "报修记录";
  if (type === "audit") return "操作日志";
  return "列表";
}

function statusBadge(status, text) {
  const klass = status === "APPROVED" || status === "DONE" ? "approved" : status === "REJECTED" || status === "CANCELED" ? "rejected" : "pending";
  return `<span class="status ${klass}">${escapeHtml(text)}</span>`;
}

function renderTimeline(targetId, items, renderer) {
  const target = $(`#${targetId}`);
  if (!target) return;
  if (!items.length) {
    target.innerHTML = `<div class="empty-note">暂无记录</div>`;
    return;
  }
  target.innerHTML = items.slice(0, 5).map(renderer).join("");
}

function requestTimelineItem(request) {
  const endText = request.handledAt ? `${escapeHtml(request.handledAt)} · ${escapeHtml(request.statusText)}` : escapeHtml(request.statusText);
  return `
    <div class="timeline-item ${request.status === "APPROVED" ? "done" : request.status === "REJECTED" || request.status === "CANCELED" ? "failed" : ""}">
      <div><strong>${escapeHtml(request.currentDormNumber)} / ${escapeHtml(request.currentBedNumber)} → ${escapeHtml(request.targetDormNumber)} / ${escapeHtml(request.targetBedNumber)}</strong><span>${escapeHtml(request.createdAt)} 提交</span></div>
      <em>${endText}</em>
    </div>
  `;
}

function repairTimelineItem(repair) {
  const endText = repair.handledAt ? `${escapeHtml(repair.handledAt)} · ${escapeHtml(repair.statusText)}` : escapeHtml(repair.statusText);
  return `
    <div class="timeline-item ${repair.status === "DONE" ? "done" : repair.status === "REJECTED" || repair.status === "CANCELED" ? "failed" : ""}">
      <div><strong>${escapeHtml(repair.category)} · ${escapeHtml(repair.dormNumber)}</strong><span>${escapeHtml(repair.createdAt)} 提交</span></div>
      <em>${endText}</em>
    </div>
  `;
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
    toast(data.message || "操作失败", "error");
    throw new Error(data.message || "操作失败");
  }
  return data;
}

let toastTimer = 0;
function toast(message, kind = "success") {
  const box = $("#toast");
  box.textContent = message;
  box.dataset.kind = kind;
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
