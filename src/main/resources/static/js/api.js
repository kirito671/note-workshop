(() => {
  const TOKEN_KEY = "note_workshop_token";

  class ApiError extends Error {
    constructor(message, status, code, payload) {
      super(message);
      this.name = "ApiError";
      this.status = status;
      this.code = code;
      this.payload = payload;
    }
  }

  const getToken = () => localStorage.getItem(TOKEN_KEY) || "";
  const setToken = (token) => localStorage.setItem(TOKEN_KEY, token);
  const clearToken = () => localStorage.removeItem(TOKEN_KEY);

  async function request(path, options = {}) {
    const method = options.method || "GET";
    const headers = { Accept: "application/json", ...(options.headers || {}) };
    let body = options.body;

    if (body !== undefined && !(body instanceof FormData)) {
      headers["Content-Type"] = headers["Content-Type"] || "application/json";
      body = JSON.stringify(body);
    }
    if (options.auth !== false && getToken()) {
      headers.Authorization = `Bearer ${getToken()}`;
    }

    let response;
    try {
      response = await fetch(path, { method, headers, body });
    } catch (error) {
      throw new ApiError("网络连接失败，请确认后端服务已启动。", 0, 0, null);
    }

    const contentType = response.headers.get("content-type") || "";
    let payload = null;
    if (contentType.includes("application/json")) {
      payload = await response.json();
    } else {
      payload = await response.text();
    }

    const success = response.ok && (!payload || payload.code === 0);
    if (!success) {
      const message = payload && payload.message ? payload.message : `请求失败（HTTP ${response.status}）`;
      throw new ApiError(message, response.status, payload && payload.code, payload);
    }
    return payload && Object.prototype.hasOwnProperty.call(payload, "data") ? payload.data : payload;
  }

  window.NoteAPI = {
    request,
    getToken,
    setToken,
    clearToken,
    isLoggedIn: () => Boolean(getToken()),
    ApiError
  };
})();