const state = {
  token: "",
  role: "",
  username: "",
  studentId: "",
  students: [],
  requests: [],
  studentPage: 1,
  studentPageSize: 10,
  requestPage: 1,
  requestPageSize: 10,
  studentMode: "all",
  studentParams: {},
  requestMode: "pending",
  requestParams: {},
};

const pageSizeOptions = [10, 20, 50];

const $ = (selector) => document.querySelector(selector);
const $$ = (selector) => Array.from(document.querySelectorAll(selector));

const titles = {
  dashboard: ["数据看板", "实时掌握宿舍入住和调换申请状态"],
  students: ["住宿信息", "维护学生住宿台账，支持查询、排序、添加、修改和删除"],
  requests: ["调换申请", "提交、查看与审核宿舍调换申请"],
  analytics: ["智能分析", "按楼栋或宿舍生成运营评估与建议"],
  settings: ["系统设置", "配置智能分析模型服务和运行参数"],
};

document.addEventListener("DOMContentLoaded", () => {
  $("#loginForm").addEventListener("submit", login);
  $$("[data-logout]").forEach((button) => button.addEventListener("click", logout));
  $$(".nav button").forEach((button) => button.addEventListener("click", () => navigate(button.dataset.view)));

  $("#allStudentsButton").addEventListener("click", showAllStudents);
  $("#sortStudentsButton").addEventListener("click", () => loadStudents("sorted"));
  $("#searchStudentButton").addEventListener("click", searchStudentById);
  $("#searchDormButton").addEventListener("click", searchDormNumber);
  $("#searchDepartmentClassButton").addEventListener("click", searchDepartmentClass);
  $("#studentForm").addEventListener("submit", saveStudent);
  $("#resetStudentForm").addEventListener("click", resetStudentForm);

  $("#requestForm").addEventListener("submit", submitRequest);
  $("#pendingRequestsButton").addEventListener("click", () => loadRequests("pending"));
  $("#allRequestsButton").addEventListener("click", () => loadRequests("all"));
  $("#myRequestsButton").addEventListener("click", () => loadRequests("student", { studentId: $("#requestStudentQuery").value.trim() }));
  $("#analyzeButton").addEventListener("click", analyzeDorm);
  $("#loadOccupancyButton").addEventListener("click", () => loadOccupancyDetails($("#occupancyScope").value));
  $("#modelConfigForm").addEventListener("submit", saveModelConfig);
  $("#reloadModelConfigButton").addEventListener("click", loadModelConfig);
});

async function login(event) {
  event.preventDefault();
  const form = new FormData(event.currentTarget);
  const data = await api("/api/login", { method: "POST", body: form });
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
  state.token = "";
  state.role = "";
  state.username = "";
  state.studentId = "";
  state.students = [];
  state.requests = [];
  state.studentPage = 1;
  state.requestPage = 1;
  state.studentMode = "all";
  state.studentParams = {};
  state.requestMode = "pending";
  state.requestParams = {};
  $("#appShell").classList.add("hidden");
  $("#loginPage").classList.remove("hidden");
  $("#currentRole").textContent = "";
  $("#currentUser").textContent = "";
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
  if (!isAdmin && $(".nav button[data-view='analytics']").classList.contains("active")) {
    navigate("dashboard");
  }
  if (!isAdmin && $(".nav button[data-view='settings']").classList.contains("active")) {
    navigate("dashboard");
  }
}

function navigate(view) {
  if ((view === "analytics" || view === "settings") && state.role !== "ADMIN") return;
  $$(".nav button").forEach((button) => button.classList.toggle("active", button.dataset.view === view));
  $$(".view").forEach((section) => section.classList.toggle("active", section.id === `${view}View`));
  $("#pageTitle").textContent = titles[view][0];
  $("#pageSubtitle").textContent = titles[view][1];
  if (view === "dashboard") loadOverview();
  if (view === "students") loadStudents("all");
  if (view === "requests") {
    loadRequests(state.role === "ADMIN" ? "pending" : "student", state.role === "ADMIN" ? {} : { studentId: state.studentId });
  }
  if (view === "analytics") loadOccupancyDetails($("#occupancyScope").value);
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

async function loadStudents(mode, params = {}) {
  state.studentMode = mode;
  state.studentParams = { ...params };
  const query = new URLSearchParams({ mode, ...params });
  const data = await api(`/api/students?${query.toString()}`);
  state.students = data.students;
  state.studentPage = 1;
  renderStudents();
}

function reloadStudents() {
  return loadStudents(state.studentMode, state.studentParams);
}

function showAllStudents() {
  $("#studentIdQuery").value = "";
  $("#dormQuery").value = "";
  $("#departmentQuery").value = "";
  $("#classQuery").value = "";
  return loadStudents("all");
}

function searchStudentById() {
  const studentId = $("#studentIdQuery").value.trim();
  if (!studentId) {
    toast("请输入学号");
    return;
  }
  loadStudents("student", { studentId });
}

function searchDormNumber() {
  const dormNumber = $("#dormQuery").value.trim();
  if (!dormNumber) {
    toast("请输入宿舍号");
    return;
  }
  loadStudents("dorm", { dormNumber });
}

function searchDepartmentClass() {
  const department = $("#departmentQuery").value.trim();
  const className = $("#classQuery").value.trim();
  if (!department && !className) {
    toast("请输入所在系或班级");
    return;
  }
  loadStudents("departmentClass", { department, className });
}

function renderStudents() {
  const tbody = $("#studentsTable");
  tbody.innerHTML = "";
  setPage("student", state.studentPage);
  const students = pageItems(state.students, state.studentPage, state.studentPageSize);
  if (!state.students.length) {
    tbody.innerHTML = `<tr><td colspan="${studentColumnCount()}">暂无数据</td></tr>`;
    renderPagination("studentsPagination", "student", state.students.length, state.studentPage, state.studentPageSize);
    return;
  }
  tbody.innerHTML = students.map((student) => `
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
  renderPagination("studentsPagination", "student", state.students.length, state.studentPage, state.studentPageSize);
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
  const data = new FormData(form);
  const method = form.mode.value === "edit" ? "PUT" : "POST";
  await api("/api/students", { method, body: data });
  resetStudentForm();
  await showAllStudents();
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
  $("#requestStudentQuery").value = form.studentId.value;
  form.reset();
  if (state.role !== "ADMIN" && state.studentId) {
    form.studentId.value = state.studentId;
  }
  await loadRequests("student", { studentId: $("#requestStudentQuery").value.trim() });
  toast(`申请已提交：${result.id}`);
}

async function loadRequests(mode, params = {}) {
  state.requestMode = mode;
  state.requestParams = { ...params };
  if (mode === "student" && !params.studentId) {
    state.requests = [];
    state.requestPage = 1;
    renderRequestsMessage("请输入学号查看申请记录");
    return;
  }
  const query = new URLSearchParams({ mode, ...params });
  const data = await api(`/api/requests?${query.toString()}`);
  state.requests = data.requests;
  state.requestPage = 1;
  renderRequests();
}

function renderRequests() {
  const tbody = $("#requestsTable");
  tbody.innerHTML = "";
  setPage("request", state.requestPage);
  const requests = pageItems(state.requests, state.requestPage, state.requestPageSize);
  if (!state.requests.length) {
    tbody.innerHTML = `<tr><td colspan="${requestColumnCount()}">暂无申请</td></tr>`;
    renderPagination("requestsPagination", "request", state.requests.length, state.requestPage, state.requestPageSize);
    return;
  }
  tbody.innerHTML = requests.map((request) => `
    <tr>
      <td>${escapeHtml(request.id)}</td>
      <td>${escapeHtml(request.studentId)}</td>
      <td>${escapeHtml(request.currentDormNumber)} / ${escapeHtml(request.currentBedNumber)}</td>
      <td>${escapeHtml(request.targetDormNumber)} / ${escapeHtml(request.targetBedNumber)}</td>
      <td>${statusBadge(request.status, request.statusText)}</td>
      <td>${escapeHtml(request.createdAt)}</td>
      <td>${escapeHtml(request.reason)}${request.adminComment ? ` / ${escapeHtml(request.adminComment)}` : ""}</td>
      ${state.role === "ADMIN" ? `<td>${request.status === "PENDING" ? `<div class="row-actions">
        <button class="btn success" onclick="decideRequest('${escapeJs(request.id)}', true)"><svg><use href="#icon-check"></use></svg>同意</button>
        <button class="btn danger" onclick="decideRequest('${escapeJs(request.id)}', false)"><svg><use href="#icon-x"></use></svg>拒绝</button>
      </div>` : ""}</td>` : ""}
    </tr>
  `).join("");
  renderPagination("requestsPagination", "request", state.requests.length, state.requestPage, state.requestPageSize);
}

function renderRequestsMessage(message) {
  $("#requestsTable").innerHTML = `<tr><td colspan="${requestColumnCount()}">${escapeHtml(message)}</td></tr>`;
  renderPagination("requestsPagination", "request", 0, state.requestPage, state.requestPageSize);
}

async function decideRequest(requestId, approved) {
  const comment = prompt("请输入审核意见：", approved ? "同意调换" : "暂不同意");
  if (comment === null) return;
  const body = new FormData();
  body.set("requestId", requestId);
  body.set("comment", comment);
  await api(approved ? "/api/requests/approve" : "/api/requests/reject", { method: "POST", body });
  await loadRequests(state.requestMode, state.requestParams);
  await loadOverview();
  toast("审批已完成");
}

async function analyzeDorm() {
  const scope = $("#analysisScope").value;
  const value = $("#analysisValue").value.trim();
  if (!value) {
    toast("请输入楼栋号或宿舍号");
    return;
  }
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

async function loadOccupancyDetails(scope) {
  const query = new URLSearchParams({ scope });
  const data = await api(`/api/occupancy?${query.toString()}`);
  renderOccupancyDetails(data.items);
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
  return state.role === "ADMIN" ? 8 : 7;
}

function pageItems(items, page, pageSize) {
  const start = (page - 1) * pageSize;
  return items.slice(start, start + pageSize);
}

function renderPagination(targetId, type, totalItems, currentPage, pageSize) {
  const target = $(`#${targetId}`);
  if (!target) return;
  const totalPages = Math.max(1, Math.ceil(totalItems / pageSize));
  const safePage = Math.min(Math.max(currentPage, 1), totalPages);
  if (safePage !== currentPage) {
    setPage(type, safePage);
    return;
  }
  const firstItem = totalItems === 0 ? 0 : (safePage - 1) * pageSize + 1;
  const lastItem = Math.min(safePage * pageSize, totalItems);
  const disabledPrevious = safePage <= 1 ? "disabled" : "";
  const disabledNext = safePage >= totalPages ? "disabled" : "";
  const options = pageSizeOptions.map((size) =>
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
  setPage(type, page);
  renderPagedTable(type);
}

function changePageSize(type, pageSize) {
  const size = Number(pageSize);
  if (!pageSizeOptions.includes(size)) return;
  if (type === "student") {
    state.studentPageSize = size;
    state.studentPage = 1;
  } else {
    state.requestPageSize = size;
    state.requestPage = 1;
  }
  renderPagedTable(type);
}

function setPage(type, page) {
  if (type === "student") {
    const totalPages = Math.max(1, Math.ceil(state.students.length / state.studentPageSize));
    state.studentPage = Math.min(Math.max(Number(page) || 1, 1), totalPages);
  } else {
    const totalPages = Math.max(1, Math.ceil(state.requests.length / state.requestPageSize));
    state.requestPage = Math.min(Math.max(Number(page) || 1, 1), totalPages);
  }
}

function renderPagedTable(type) {
  if (type === "student") {
    renderStudents();
  } else {
    renderRequests();
  }
}

function statusBadge(status, text) {
  const klass = status === "APPROVED" ? "approved" : status === "REJECTED" ? "rejected" : "pending";
  return `<span class="status ${klass}">${escapeHtml(text)}</span>`;
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
