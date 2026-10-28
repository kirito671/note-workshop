(() => {
  const api = window.NoteAPI;
  const $ = (selector) => document.querySelector(selector);
  const state = {
    view: "explore",
    page: 1,
    size: 8,
    keyword: "",
    user: null,
    editingNoteId: null,
    detail: null,
    detailImages: [],
    imageCache: new Map()
  };

  const IMAGE_CACHE_KEY = "note_workshop_image_cache_v1";

  const viewMeta = {
    explore: { label: "发现 / 公开书架", title: "公开书架", endpoint: "/api/notes/public" },
    my: { label: "我的书架 / 写过的页", title: "我的书架", endpoint: "/api/notes/my" },
    liked: { label: "我的书架 / 我点赞的", title: "我点赞的", endpoint: "/api/notes/liked" },
    ranking: { label: "发现 / 热度排行", title: "热度排行", endpoint: "/api/notes/ranking" }
  };

  const el = (tag, className, text) => {
    const node = document.createElement(tag);
    if (className) node.className = className;
    if (text !== undefined) node.textContent = text;
    return node;
  };

  let toastTimer = null;
  function toast(message, isError = false) {
    const node = $("#toast");
    node.textContent = message;
    node.classList.toggle("is-error", isError);
    node.classList.add("is-visible");
    window.clearTimeout(toastTimer);
    toastTimer = window.setTimeout(() => node.classList.remove("is-visible"), 3600);
  }

  function loadImageCache() {
    try {
      const raw = JSON.parse(localStorage.getItem(IMAGE_CACHE_KEY) || "{}");
      Object.entries(raw).forEach(([noteId, images]) => {
        if (Array.isArray(images)) state.imageCache.set(Number(noteId), images);
      });
    } catch (error) {
      localStorage.removeItem(IMAGE_CACHE_KEY);
    }
  }

  function persistImageCache() {
    const raw = {};
    state.imageCache.forEach((images, noteId) => { raw[noteId] = images; });
    localStorage.setItem(IMAGE_CACHE_KEY, JSON.stringify(raw));
  }

  function clearImageCache() {
    state.imageCache.clear();
    localStorage.removeItem(IMAGE_CACHE_KEY);
  }

  function formatDate(value) {
    if (!value) return "";
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? "" : date.toLocaleDateString("zh-CN", { year: "numeric", month: "short", day: "numeric" });
  }

  function initials(name) {
    return (name || "NW").trim().slice(0, 2).toUpperCase();
  }

  function setAvatar(node, user) {
    node.replaceChildren();
    if (user && user.avatarUrl) {
      const image = el("img");
      image.src = user.avatarUrl;
      image.alt = `${user.nickname || "用户"}的头像`;
      node.append(image);
    } else {
      node.textContent = initials(user && (user.nickname || user.username));
    }
  }

  function renderUser(user) {
    state.user = user;
    $("#railName").textContent = user ? (user.nickname || user.username) : "访客";
    $("#railUsername").textContent = user ? `@${user.username}` : "尚未登录";
    $("#sessionLabel").textContent = user ? `已登录 · ${user.nickname || user.username}` : "访客模式";
    setAvatar($("#railAvatar"), user);
    setAvatar($("#profileAvatar"), user);
    $("#profileName").textContent = user ? (user.nickname || user.username) : "访客";
    $("#profileUsername").textContent = user ? `@${user.username}` : "尚未登录";
    $("#profileNickname").value = user ? user.nickname || "" : "";
    $("#logoutButton").hidden = !user;
  }

  function showAuth() {
    $("#authView").hidden = false;
    $("#workspace").hidden = true;
    $("#sessionLabel").textContent = "访客模式";
    $("#logoutButton").hidden = true;
  }

  function showWorkspace() {
    $("#authView").hidden = true;
    $("#workspace").hidden = false;
  }

  function switchAuthMode(mode) {
    document.querySelectorAll(".auth-tab").forEach((tab) => {
      tab.classList.toggle("is-active", tab.dataset.authMode === mode);
    });
    $("#loginForm").hidden = mode !== "login";
    $("#registerForm").hidden = mode !== "register";
  }

  async function loadProfile() {
    renderUser(await api.request("/api/users/me"));
  }

  async function enterApp() {
    await loadProfile();
    showWorkspace();
    switchView("explore");
  }

  async function handleLogin(event) {
    event.preventDefault();
    try {
      const data = await api.request("/api/auth/login", {
        method: "POST",
        auth: false,
        body: { username: $("#loginUsername").value.trim(), password: $("#loginPassword").value }
      });
      api.setToken(data.token);
      await enterApp();
      toast("已进入阅读工作台。");
    } catch (error) {
      handleError(error);
    }
  }

  async function handleRegister(event) {
    event.preventDefault();
    const username = $("#registerUsername").value.trim();
    const password = $("#registerPassword").value;
    const nickname = $("#registerNickname").value.trim();
    try {
      await api.request("/api/auth/register", {
        method: "POST",
        auth: false,
        body: { username, password, nickname }
      });
      const data = await api.request("/api/auth/login", {
        method: "POST",
        auth: false,
        body: { username, password }
      });
      api.setToken(data.token);
      await enterApp();
      toast("书架已创建，欢迎回来。");
    } catch (error) {
      handleError(error);
    }
  }

  function logout() {
    api.clearToken();
    state.user = null;
    state.detail = null;
    state.detailImages = [];
    clearImageCache();
    $("#detailDrawer").classList.remove("is-open");
    $("#profileModal").hidden = true;
    renderUser(null);
    showAuth();
    switchAuthMode("login");
    toast("已退出登录。");
  }

  function handleError(error) {
    if (error && error.status === 401 && api.isLoggedIn()) {
      api.clearToken();
      clearImageCache();
      renderUser(null);
      showAuth();
    }
    toast(error && error.message ? error.message : "操作失败，请稍后再试。", true);
  }

  function setViewVisibility(view) {
    document.querySelectorAll(".view-panel").forEach((panel) => {
      panel.hidden = panel.id !== `${view}View`;
    });
    document.querySelectorAll(".nav-item").forEach((item) => {
      item.classList.toggle("is-active", item.dataset.view === view);
    });
  }

  function viewElements(view) {
    if (view === "explore") return { list: $("#notesList"), pagination: $("#pagination"), subtitle: $("#listSubtitle"), meta: $("#resultMeta") };
    if (view === "my") return { list: $("#myNotesList"), pagination: $("#myPagination"), subtitle: $("#mySubtitle"), meta: null };
    if (view === "liked") return { list: $("#likedNotesList"), pagination: $("#likedPagination"), subtitle: null, meta: null };
    return { list: $("#rankingNotesList"), pagination: $("#rankingPagination"), subtitle: null, meta: null };
  }

  async function loadView(view = state.view) {
    if (!api.isLoggedIn()) {
      showAuth();
      return;
    }
    state.view = view;
    setViewVisibility(view);
    $("#crumb").textContent = viewMeta[view].label;
    const target = viewElements(view);
    target.list.replaceChildren();
    if (target.subtitle) target.subtitle.textContent = "正在加载";
    if (target.meta) target.meta.textContent = "正在整理书架…";
    try {
      const query = new URLSearchParams({ page: String(state.page), size: String(state.size) });
      if (view === "explore" && state.keyword) query.set("keyword", state.keyword);
      const path = `${viewMeta[view].endpoint}?${query.toString()}`;
      const pageData = await api.request(path);
      renderNotes(pageData.records || [], target.list, view);
      renderPagination(target.pagination, pageData, view);
      if (target.subtitle) target.subtitle.textContent = `${pageData.total || 0} 篇`;
      if (target.meta) target.meta.textContent = `${pageData.total || 0} 篇笔记，按最近更新展示`;
    } catch (error) {
      renderEmpty(target.list, "暂时无法打开这页。", "请检查网络或稍后再试。");
      if (target.subtitle) target.subtitle.textContent = "加载失败";
      handleError(error);
    }
  }
  function renderEmpty(container, title, description) {
    container.replaceChildren();
    const empty = el("div", "empty-state");
    empty.append(el("strong", "", title), el("span", "", description));
    container.append(empty);
  }

  function renderNotes(notes, container, mode) {
    container.replaceChildren();
    if (!notes.length) {
      renderEmpty(container, "这里还没有笔记。", mode === "my" ? "写下一篇，让你的书架从这里开始。" : "换个关键词，或者先成为第一个留下想法的人。");
      return;
    }
    notes.forEach((note, index) => {
      const card = el("article", mode === "explore" ? "note-card" : "note-row");
      card.tabIndex = 0;
      card.setAttribute("role", "button");
      card.setAttribute("aria-label", `打开笔记：${note.title}`);
      card.addEventListener("click", () => openDetail(note.id));
      card.addEventListener("keydown", (event) => {
        if (event.key === "Enter" || event.key === " ") {
          event.preventDefault();
          openDetail(note.id);
        }
      });

      const top = el("div", mode === "explore" ? "note-card__top" : "note-row__top");
      top.append(el("span", mode === "explore" ? "note-card__book" : "note-row__book", note.bookName || "未命名图书"));
      top.append(el("span", mode === "explore" ? "note-card__badge" : "note-row__badge", note.visibility === 1 ? "公开" : "私密"));

      const title = el("h3", "", note.title || "未命名笔记");
      const footer = el("div", mode === "explore" ? "note-card__footer" : "note-row__footer");
      const meta = el("div", "note-row__meta");
      if (mode === "ranking") meta.append(el("span", "", `${String(index + 1).padStart(2, "0")} / 热度`));
      meta.append(el("span", "", formatDate(note.createdAt)));
      const like = el("button", `like-chip${note.likedByMe ? " is-liked" : ""}`, `${note.likedByMe ? "♥" : "♡"}  ${note.likeCount || 0}`);
      like.type = "button";
      like.addEventListener("click", (event) => {
        event.stopPropagation();
        toggleLike(note.id, note.likedByMe);
      });
      footer.append(meta, like);

      if (mode === "explore") {
        card.append(top, title, footer);
      } else {
        const left = el("div");
        left.append(top, title, footer);
        card.append(left);
      }
      container.append(card);
    });
  }

  function renderPagination(container, pageData, view) {
    container.replaceChildren();
    const pages = Number(pageData.pages || 0);
    if (pages <= 1) return;
    const current = Number(pageData.page || 1);
    const prev = el("button", "page-button", "上一页");
    prev.type = "button";
    prev.disabled = current <= 1;
    prev.addEventListener("click", () => { state.page = current - 1; loadView(view); });
    const count = el("span", "page-button is-current", `${current} / ${pages}`);
    const next = el("button", "page-button", "下一页");
    next.type = "button";
    next.disabled = current >= pages;
    next.addEventListener("click", () => { state.page = current + 1; loadView(view); });
    container.append(prev, count, next);
  }

  async function toggleLike(noteId, likedByMe) {
    try {
      const result = await api.request(`/api/notes/${noteId}/like`, { method: likedByMe ? "DELETE" : "POST" });
      toast(result.liked ? "已放进喜欢。" : "已取消喜欢。");
      if (state.detail && state.detail.id === noteId) {
        state.detail.likedByMe = result.liked;
        state.detail.likeCount = result.likeCount;
        renderDetail();
      }
      await loadView(state.view);
    } catch (error) {
      handleError(error);
    }
  }

  function resetComposer() {
    state.editingNoteId = null;
    $("#noteFormTitle").textContent = "写一篇笔记";
    $("#noteForm").reset();
    $("#noteVisibility").value = "1";
  }

  function openComposer(note = null) {
    $("#noteForm").hidden = false;
    $("#toggleComposer").textContent = note ? "正在编辑" : "收起编辑器";
    if (note) {
      state.editingNoteId = note.id;
      $("#noteFormTitle").textContent = "编辑这篇笔记";
      $("#noteTitle").value = note.title || "";
      $("#noteBookName").value = note.bookName || "";
      $("#noteContent").value = note.content || "";
      $("#noteVisibility").value = String(note.visibility ?? 1);
    } else {
      resetComposer();
    }
    $("#noteTitle").focus();
  }

  async function handleNoteSubmit(event) {
    event.preventDefault();
    const payload = {
      title: $("#noteTitle").value.trim(),
      bookName: $("#noteBookName").value.trim(),
      content: $("#noteContent").value.trim(),
      visibility: Number($("#noteVisibility").value)
    };
    try {
      if (state.editingNoteId) {
        await api.request(`/api/notes/${state.editingNoteId}`, { method: "PUT", body: payload });
        toast("笔记已更新。");
      } else {
        await api.request("/api/notes", { method: "POST", body: payload });
        toast("笔记已放进书架。");
      }
      $("#noteForm").hidden = true;
      resetComposer();
      await loadView("my");
    } catch (error) {
      handleError(error);
    }
  }

  async function openDetail(noteId) {
    try {
      const detail = await api.request(`/api/notes/${noteId}`);
      state.detail = detail;
      const cached = state.imageCache.get(noteId) || [];
      state.detailImages = (detail.images || []).map((url) => {
        const known = cached.find((item) => item.imageUrl === url);
        return known ? { ...known, id: known.id } : { id: null, imageUrl: url, sortOrder: null };
      });
      renderDetail();
      $("#detailDrawer").classList.add("is-open");
      $("#detailDrawer").setAttribute("aria-hidden", "false");
      document.body.classList.add("drawer-open");
    } catch (error) {
      handleError(error);
    }
  }

  function closeDetail() {
    $("#detailDrawer").classList.remove("is-open");
    $("#detailDrawer").setAttribute("aria-hidden", "true");
    document.body.classList.remove("drawer-open");
  }

  function renderDetail() {
    const detail = state.detail;
    const content = $("#detailContent");
    content.replaceChildren();
    if (!detail) return;
    const header = el("header", "detail-head");
    header.append(el("p", "eyebrow", detail.visibility === 1 ? "from the public shelf" : "a private page"));
    header.append(el("h2", "", detail.title || "未命名笔记"));
    const meta = el("div", "detail-meta");
    meta.append(el("span", "", detail.bookName || "未命名图书"));
    meta.append(el("span", "detail-status", detail.status === 1 ? "正常展示" : "管理员隐藏"));
    meta.append(el("span", "", formatDate(detail.createdAt)));
    header.append(meta);

    const body = el("div", "detail-body", detail.content || "");
    const actions = el("div", "detail-actions");
    const like = el("button", `detail-like${detail.likedByMe ? " is-liked" : ""}`, `${detail.likedByMe ? "♥ 已喜欢" : "♡ 喜欢"} · ${detail.likeCount || 0}`);
    like.type = "button";
    like.addEventListener("click", () => toggleLike(detail.id, detail.likedByMe));
    actions.append(like);
    if (state.user && state.user.id === detail.userId) {
      const edit = el("button", "secondary-button", "编辑笔记");
      edit.type = "button";
      edit.addEventListener("click", () => {
        closeDetail();
        switchView("my");
        openComposer(detail);
      });
      const remove = el("button", "quiet-button", "删除笔记");
      remove.type = "button";
      remove.addEventListener("click", () => deleteNote(detail.id));
      actions.append(edit, remove);
    }
    content.append(header, body, actions);
    renderDetailImages(content);
  }
  function renderDetailImages(content) {
    const section = el("section", "detail-section");
    const head = el("div", "detail-section-head");
    head.append(el("h3", "", "页边图像"));
    const uploadRow = el("div", "upload-row");
    const input = el("input");
    input.type = "file";
    input.accept = ".jpg,.jpeg,.png,image/jpeg,image/png";
    input.multiple = true;
    input.id = "detailImageInput";
    const uploadButton = el("button", "secondary-button", "上传图片");
    uploadButton.type = "button";
    uploadButton.addEventListener("click", () => uploadDetailImages());
    uploadRow.append(input, uploadButton);
    section.append(head);

    if (!state.detailImages.length) {
      section.append(el("p", "image-note", "这篇笔记还没有配图。"));
    } else {
      const grid = el("div", "image-grid");
      state.detailImages.forEach((image, index) => {
        const card = el("div", "detail-image-card");
        const img = el("img");
        img.src = image.imageUrl;
        img.alt = `笔记图片 ${index + 1}`;
        img.loading = "lazy";
        card.append(img);
        if (image.id) {
          const controls = el("div", "image-controls");
          const up = el("button", "", "上移");
          up.type = "button";
          up.disabled = index === 0;
          up.addEventListener("click", () => moveImage(index, -1));
          const down = el("button", "", "下移");
          down.type = "button";
          down.disabled = index === state.detailImages.length - 1;
          down.addEventListener("click", () => moveImage(index, 1));
          const remove = el("button", "", "删除");
          remove.type = "button";
          remove.addEventListener("click", () => deleteDetailImage(image));
          controls.append(up, down, remove);
          card.append(controls);
        }
        grid.append(card);
      });
      section.append(grid);
      if (state.detailImages.some((image) => !image.id)) {
        section.append(el("p", "image-note", "历史图片来自详情接口的 URL 列表，当前页面只能查看；新上传的图片可以立即排序和删除。"));
      }
    }
    section.append(uploadRow);
    content.append(section);
  }

  async function uploadDetailImages() {
    const input = $("#detailImageInput");
    const files = input && input.files ? Array.from(input.files) : [];
    if (!files.length) {
      toast("先选择一张图片。", true);
      return;
    }
    const formData = new FormData();
    files.forEach((file) => formData.append("files", file));
    try {
      const uploaded = await api.request(`/api/notes/${state.detail.id}/images`, { method: "POST", body: formData });
      const cache = state.imageCache.get(state.detail.id) || [];
      const merged = [...cache.filter((item) => !uploaded.some((newItem) => newItem.imageUrl === item.imageUrl)), ...uploaded];
      state.imageCache.set(state.detail.id, merged);
      persistImageCache();
      state.detailImages = [...state.detailImages, ...uploaded];
      renderDetail();
      toast(`已加入 ${uploaded.length} 张图片。`);
      await loadView(state.view);
    } catch (error) {
      handleError(error);
    }
  }

  async function deleteDetailImage(image) {
    if (!image.id) return;
    try {
      await api.request(`/api/notes/${state.detail.id}/images/${image.id}`, { method: "DELETE" });
      state.detailImages = state.detailImages.filter((item) => item.id !== image.id);
      const cache = state.imageCache.get(state.detail.id) || [];
      state.imageCache.set(state.detail.id, cache.filter((item) => item.id !== image.id));
      persistImageCache();
      renderDetail();
      toast("图片已删除。");
      await loadView(state.view);
    } catch (error) {
      handleError(error);
    }
  }

  async function moveImage(index, delta) {
    const target = index + delta;
    if (target < 0 || target >= state.detailImages.length) return;
    if (state.detailImages.some((image) => !image.id)) {
      toast("当前有历史图片缺少图片 ID，暂时不能调整顺序。", true);
      return;
    }
    const next = [...state.detailImages];
    [next[index], next[target]] = [next[target], next[index]];
    try {
      const updated = await api.request(`/api/notes/${state.detail.id}/images/order`, {
        method: "PUT",
        body: { imageIds: next.map((image) => image.id) }
      });
      state.detailImages = updated.map((image) => ({ ...image }));
      state.imageCache.set(state.detail.id, [...state.detailImages]);
      persistImageCache();
      renderDetail();
      toast("图片顺序已更新。");
    } catch (error) {
      handleError(error);
    }
  }

  async function deleteNote(noteId) {
    if (!window.confirm("删除这篇笔记？关联的图片和点赞记录也会一起清理。")) return;
    try {
      await api.request(`/api/notes/${noteId}`, { method: "DELETE" });
      closeDetail();
      toast("笔记已删除。");
      await loadView(state.view);
    } catch (error) {
      handleError(error);
    }
  }

  function openProfile() {
    if (!state.user) {
      showAuth();
      toast("先登录再编辑个人资料。", true);
      return;
    }
    renderUser(state.user);
    $("#profileModal").hidden = false;
  }

  function closeProfile() {
    $("#profileModal").hidden = true;
  }

  async function handleProfileSubmit(event) {
    event.preventDefault();
    try {
      const updated = await api.request("/api/users/me", {
        method: "PUT",
        body: { nickname: $("#profileNickname").value.trim() }
      });
      renderUser(updated);
      toast("昵称已更新。");
    } catch (error) {
      handleError(error);
    }
  }

  async function handleAvatarSubmit(event) {
    event.preventDefault();
    const file = $("#avatarInput").files[0];
    if (!file) {
      toast("先选择一张头像图片。", true);
      return;
    }
    const formData = new FormData();
    formData.append("file", file);
    try {
      const updated = await api.request("/api/users/me/avatar", { method: "POST", body: formData });
      renderUser(updated);
      toast("头像已更新。");
      $("#avatarInput").value = "";
    } catch (error) {
      handleError(error);
    }
  }

  function switchView(view) {
    state.view = view;
    state.page = 1;
    setViewVisibility(view);
    $("#crumb").textContent = viewMeta[view].label;
    loadView(view);
  }

  function bindEvents() {
    document.querySelectorAll("[data-auth-mode]").forEach((tab) => {
      tab.addEventListener("click", () => switchAuthMode(tab.dataset.authMode));
    });
    $("#loginForm").addEventListener("submit", handleLogin);
    $("#registerForm").addEventListener("submit", handleRegister);
    $("#logoutButton").addEventListener("click", logout);
    document.querySelectorAll(".nav-item").forEach((item) => {
      item.addEventListener("click", () => {
        if (!state.user) {
          showAuth();
          return;
        }
        switchView(item.dataset.view);
      });
    });
    $("#searchForm").addEventListener("submit", (event) => {
      event.preventDefault();
      state.keyword = $("#searchInput").value.trim();
      state.page = 1;
      loadView("explore");
    });
    $("#toggleComposer").addEventListener("click", () => {
      if ($("#noteForm").hidden) {
        openComposer();
      } else {
        $("#noteForm").hidden = true;
        resetComposer();
        $("#toggleComposer").textContent = "写一篇笔记";
      }
    });
    $("#cancelEdit").addEventListener("click", () => {
      $("#noteForm").hidden = true;
      resetComposer();
      $("#toggleComposer").textContent = "写一篇笔记";
    });
    $("#noteForm").addEventListener("submit", handleNoteSubmit);
    $("#profileButton").addEventListener("click", openProfile);
    $("#profileForm").addEventListener("submit", handleProfileSubmit);
    $("#avatarForm").addEventListener("submit", handleAvatarSubmit);
    document.querySelectorAll("[data-close-detail]").forEach((node) => node.addEventListener("click", closeDetail));
    document.querySelectorAll("[data-close-profile]").forEach((node) => node.addEventListener("click", closeProfile));
    document.addEventListener("keydown", (event) => {
      if (event.key === "Escape") {
        closeDetail();
        closeProfile();
      }
    });
  }

  async function init() {
    loadImageCache();
    bindEvents();
    if (api.isLoggedIn()) {
      try {
        await enterApp();
      } catch (error) {
        api.clearToken();
        showAuth();
        handleError(error);
      }
    } else {
      showAuth();
      renderUser(null);
    }
  }

  init();
})();