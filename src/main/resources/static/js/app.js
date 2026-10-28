(() => {
  const api = window.NoteAPI;
  const $ = (selector) => document.querySelector(selector);

  const state = {
    view: "explore", page: 1, size: 8, keyword: "", user: null,
    editingNoteId: null, detail: null, detailImages: [], comments: [],
    followStatus: null, replyTo: null, analysisDraft: null,
    currentConversationId: null, conversations: [], adminTab: "review", author: null
  };

  const viewLabels = {
    explore: "公开书架", my: "我的笔记", liked: "我点赞的", ranking: "热度排行",
    authors: "发现作者", assistant: "AI 客服", admin: "管理后台"
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

  function initials(name) { return (name || "NW").trim().slice(0, 2).toUpperCase(); }

  function setAvatar(node, profile) {
    node.replaceChildren();
    if (profile && profile.avatarUrl) {
      const image = el("img");
      image.src = profile.avatarUrl;
      image.alt = `${profile.nickname || "用户"}的头像`;
      node.append(image);
    } else {
      node.textContent = initials(profile && (profile.nickname || profile.username));
    }
  }

  function formatDate(value) {
    if (!value) return "";
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? "" : date.toLocaleDateString("zh-CN", { year: "numeric", month: "short", day: "numeric" });
  }

  function formatDateTime(value) {
    if (!value) return "";
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? "" : date.toLocaleString("zh-CN", { month: "short", day: "numeric", hour: "2-digit", minute: "2-digit" });
  }

  function statusInfo(note) {
    if (note.status === 0) return { label: "已隐藏", className: "hidden" };
    if (note.visibility === 0) return { label: "私有", className: "" };
    if (note.auditStatus === 0) return { label: "待审核", className: "pending" };
    if (note.auditStatus === 2) return { label: "已驳回", className: "rejected" };
    return { label: "已公开", className: "approved" };
  }

  function badge(note) {
    const info = statusInfo(note);
    return el("span", `status-badge ${info.className}`.trim(), info.label);
  }

  function emptyState(title, description) {
    const box = el("div", "empty-state");
    box.append(el("strong", "", title), el("p", "", description));
    return box;
  }

  function handleError(error) {
    if (error && error.status === 401) {
      api.clearToken();
      state.user = null;
      showAuth();
      toast("登录已失效，请重新登录。", true);
      return;
    }
    toast(error && error.message ? error.message : "操作失败，请稍后重试。", true);
  }

  function renderUser(user) {
    state.user = user;
    $("#railName").textContent = user.nickname || user.username;
    $("#railUsername").textContent = `@${user.username}`;
    $("#sessionNote").textContent = user.role === 1 ? "管理员会话" : "阅读者会话";
    $("#railStatus").textContent = user.role === 1 ? "你正在维护公 书架的秩序与质量。" : "每一次阅读，都会留下可回看的路径。";
    setAvatar($("#railAvatar"), user);
    $("#profileAvatar").className = "avatar avatar-large";
    setAvatar($("#profileAvatar"), user);
    $("#profileName").textContent = user.nickname || user.username;
    $("#profileUsername").textContent = `@${user.username}`;
    $("#profileNickname").value = user.nickname || "";
    $("#adminNav").hidden = user.role !== 1;
    $("#logoutButton").hidden = false;
  }

  function showAuth() {
    $("#authView").hidden = false;
    $("#workspace").hidden = true;
    $("#logoutButton").hidden = true;
    $("#adminNav").hidden = true;
    $("#profileModal").hidden = true;
    closeDetail();
    closeAuthor();
  }

  function enterApp(user) {
    renderUser(user);
    $("#authView").hidden = true;
    $("#workspace").hidden = false;
    switchView("explore");
  }

  function switchAuthMode(mode) {
    const isLogin = mode === "login";
    $("#loginForm").hidden = !isLogin;
    $("#registerForm").hidden = isLogin;
    document.querySelectorAll("[data-auth-mode]").forEach((button) => {
      button.classList.toggle("is-active", button.dataset.authMode === mode);
    });
  }

  async function loginWithCredentials(username, password) {
    const session = await api.request("/api/auth/login", { method: "POST", auth: false, body: { username, password } });
    api.setToken(session.token);
    const user = await api.request("/api/users/me");
    enterApp(user);
  }

  async function handleLogin(event) {
    event.preventDefault();
    try {
      await loginWithCredentials($("#loginUsername").value.trim(), $("#loginPassword").value);
      $("#loginForm").reset();
      toast("已进入阅读工作台。");
    } catch (error) { handleError(error); }
  }

  async function handleRegister(event) {
    event.preventDefault();
    try {
      const username = $("#registerUsername").value.trim();
      const password = $("#registerPassword").value;
      const nickname = $("#registerNickname").value.trim();
      await api.request("/api/auth/register", { method: "POST", auth: false, body: { username, password, nickname } });
      await loginWithCredentials(username, password);
      $("#registerForm").reset();
      toast("账号已创建，欢迎开始记录。");
    } catch (error) { handleError(error); }
  }

  function logout() {
    api.clearToken();
    state.user = null;
    state.detail = null;
    state.conversations = [];
    state.currentConversationId = null;
    showAuth();
    toast("已退出登录。");
  }

  function createLikeButton(note) {
    const button = el("button", `like-button${note.likedByMe ? " is-liked" : ""}`, `${note.likedByMe ? "已赞" : "点赞"} ${note.likeCount || 0}`);
    button.type = "button";
    button.addEventListener("click", (event) => { event.stopPropagation(); toggleLike(note); });
    return button;
  }

  function createAuthorButton(note, className = "note-author") {
    const button = el("button", className);
    button.type = "button";
    const avatar = el("span", "avatar");
    setAvatar(avatar, { nickname: note.authorNickname, avatarUrl: note.authorAvatarUrl });
    avatar.style.width = "25px";
    avatar.style.height = "25px";
    avatar.style.flexBasis = "25px";
    avatar.style.fontSize = "0.58rem";
    button.append(avatar, el("span", "", note.authorNickname || `作者 #${note.userId}`));
    button.addEventListener("click", (event) => { event.stopPropagation(); openAuthor(note.userId); });
    return button;
  }

  function createNoteCard(note) {
    const card = el("article", "note-card");
    card.tabIndex = 0;
    card.addEventListener("click", () => openDetail(note.id));
    card.addEventListener("keydown", (event) => {
      if (event.key === "Enter" || event.key === " ") { event.preventDefault(); openDetail(note.id); }
    });
    const top = el("div", "note-card-top");
    top.append(createAuthorButton(note), badge(note));
    const foot = el("div", "note-card-foot");
    foot.append(el("span", "", formatDate(note.createdAt)), createLikeButton(note));
    card.append(top, el("h3", "", note.title), el("p", "note-book", note.bookName), foot);
    return card;
  }

  function createNoteRow(note, mode) {
    const row = el("article", "note-row");
    row.tabIndex = 0;
    row.addEventListener("click", () => openDetail(note.id));
    row.addEventListener("keydown", (event) => {
      if (event.key === "Enter" || event.key === " ") { event.preventDefault(); openDetail(note.id); }
    });
    const main = el("div");
    const top = el("div", "note-row-top");
    top.append(createAuthorButton(note), badge(note));
    main.append(top, el("h3", "", note.title), el("p", "", `${note.bookName} · ${formatDate(note.createdAt)}`));
    const side = el("div", "note-row-side");
    if (mode === "ranking") side.append(el("span", "", `赞 ${note.likeCount || 0}`));
    if (mode === "my") side.append(el("span", "", note.visibility === 1 ? "公开" : "私有"));
    side.append(createLikeButton(note));
    row.append(main, side);
    return row;
  }

  function renderNotes(records, container, mode = "card") {
    container.replaceChildren();
    if (!records.length) {
      container.append(emptyState("这里还没有页面", "从写下一篇笔记开始，或换一个筛选条件。"));
      return;
    }
    records.forEach((note) => container.append(mode === "card" ? createNoteCard(note) : createNoteRow(note, mode)));
  }

  function renderPagination(container, pageData, onChange) {
    container.replaceChildren();
    const pages = Number(pageData.pages || 0);
    if (pages <= 1) return;
    const current = Number(pageData.page || 1);
    const previous = el("button", "page-button", "上一页");
    previous.type = "button";
    previous.disabled = current <= 1;
    previous.addEventListener("click", () => onChange(current - 1));
    const label = el("span", "page-button is-current", `${current} / ${pages}`);
    const next = el("button", "page-button", "下一页");
    next.type = "button";
    next.disabled = current >= pages;
    next.addEventListener("click", () => onChange(current + 1));
    container.append(previous, label, next);
  }

  async function toggleLike(note) {
    try {
      const result = await api.request(`/api/notes/${note.id}/like`, { method: note.likedByMe ? "DELETE" : "POST" });
      note.likedByMe = result.liked;
      note.likeCount = result.likeCount;
      if (state.detail && state.detail.id === note.id) {
        state.detail.likedByMe = result.liked;
        state.detail.likeCount = result.likeCount;
        renderDetail();
      }
      await loadView(state.view);
      toast(result.liked ? "已加入点赞。" : "已取消点赞。");
    } catch (error) { handleError(error); }
  }

  async function loadExplore(page = state.page) {
    state.page = page;
    $("#notesList").replaceChildren(el("p", "loading-row", "正在整理书架…"));
    try {
      const data = await api.request(`/api/notes/public?page=${page}&size=${state.size}&keyword=${encodeURIComponent(state.keyword)}`);
      renderNotes(data.records, $("#notesList"), "card");
      $("#exploreMeta").textContent = `共 ${data.total} 篇 · 第 ${data.page} 页`;
      $("#clearSearch").hidden = !state.keyword;
      renderPagination($("#explorePagination"), data, loadExplore);
    } catch (error) { handleError(error); }
  }

  async function loadMy(page = 1) {
    try {
      const data = await api.request(`/api/notes/my?page=${page}&size=${state.size}`);
      renderNotes(data.records, $("#myNotesList"), "my");
      $("#myMeta").textContent = `共 ${data.total} 篇`;
      renderPagination($("#myPagination"), data, loadMy);
    } catch (error) { handleError(error); }
  }

  async function loadLiked(page = 1) {
    try {
      const data = await api.request(`/api/notes/liked?page=${page}&size=${state.size}`);
      renderNotes(data.records, $("#likedNotesList"), "liked");
      renderPagination($("#likedPagination"), data, loadLiked);
    } catch (error) { handleError(error); }
  }

  async function loadRanking(page = 1) {
    try {
      const data = await api.request(`/api/notes/ranking?page=${page}&size=${state.size}`);
      renderNotes(data.records, $("#rankingNotesList"), "ranking");
      renderPagination($("#rankingPagination"), data, loadRanking);
    } catch (error) { handleError(error); }
  }

  async function loadAuthors() {
    $("#authorsList").replaceChildren(el("p", "loading-row", "正在寻找作者…"));
    try {
      const data = await api.request("/api/users/hot-authors?page=1&size=50");
      $("#authorsList").replaceChildren();
      if (!data.records.length) {
        $("#authorsList").append(emptyState("还没有可发现的作者", "公开笔记审核通过后，作者会出现在这里。"));
        return;
      }
      for (const author of data.records) {
        const card = el("article", "author-card");
        const head = el("div", "author-card-head");
        const avatar = el("span", "avatar avatar-large");
        setAvatar(avatar, { nickname: author.nickname, avatarUrl: author.avatarUrl });
        const copy = el("div");
        copy.append(el("h3", "", author.nickname || `作者 #${author.userId}`), el("p", "", `${author.followerCount} 位关注者`));
        head.append(avatar, copy);
        const actions = el("div", "author-card-actions");
        const open = el("button", "secondary-button", "查看作者");
        open.type = "button";
        open.addEventListener("click", () => openAuthor(author.userId));
        const follow = el("button", "small-button", "关注");
        follow.type = "button";
        if (state.user && state.user.id === author.userId) {
          follow.textContent = "这是你";
          follow.disabled = true;
        } else {
          try {
            const status = await api.request(`/api/users/${author.userId}/follow-status`);
            follow.textContent = status.following ? "已关注" : "关注";
            follow.dataset.following = String(status.following);
          } catch (error) { follow.disabled = true; }
          follow.addEventListener("click", async () => {
            const following = follow.dataset.following === "true";
            try {
              const status = await api.request(`/api/users/${author.userId}/follow`, { method: following ? "DELETE" : "POST" });
              follow.textContent = status.following ? "已关注" : "关注";
              follow.dataset.following = String(status.following);
              toast(status.following ? "已关注作者。" : "已取消关注。");
              await loadAuthors();
            } catch (error) { handleError(error); }
          });
        }
        actions.append(open, follow);
        card.append(head, actions);
        $("#authorsList").append(card);
      }
    } catch (error) { handleError(error); }
  }

  function resetComposer() {
    state.editingNoteId = null;
    $("#noteForm").reset();
    $("#noteVisibility").value = "1";
    $("#noteFormTitle").textContent = "写一篇笔记";
    $("#noteFormKicker").textContent = "新页面";
    $("#toggleComposer").textContent = "写一篇笔记";
  }

  function openComposer(note = null) {
    $("#noteForm").hidden = false;
    if (note) {
      state.editingNoteId = note.id;
      $("#noteFormTitle").textContent = "编辑这篇笔记";
      $("#noteFormKicker").textContent = "修订页面";
      $("#noteTitle").value = note.title || "";
      $("#noteBookName").value = note.bookName || "";
      $("#noteContent").value = note.content || "";
      $("#noteVisibility").value = String(note.visibility ?? 1);
    } else {
      resetComposer();
    }
    $("#noteForm").hidden = false;
    $("#toggleComposer").textContent = note ? "正在编辑" : "收起编辑器";
    $("#noteTitle").focus();
  }

  async function handleNoteSubmit(event) {
    event.preventDefault();
    const payload = {
      title: $("#noteTitle").value.trim(), bookName: $("#noteBookName").value.trim(),
      content: $("#noteContent").value.trim(), visibility: Number($("#noteVisibility").value)
    };
    try {
      const saved = state.editingNoteId
        ? await api.request(`/api/notes/${state.editingNoteId}`, { method: "PUT", body: payload })
        : await api.request("/api/notes", { method: "POST", body: payload });
      resetComposer();
      $("#noteForm").hidden = true;
      await loadMy(1);
      toast(payload.visibility === 1 ? "笔记已保存，正在等待管理员审核。" : "私有笔记已保存。");
      await openDetail(saved.id);
    } catch (error) { handleError(error); }
  }

  async function openDetail(noteId) {
    $("#authorModal").hidden = true;
    try {
      const detail = await api.request(`/api/notes/${noteId}`);
      const images = await api.request(`/api/notes/${noteId}/images`).catch(() => []);
      const comments = await api.request(`/api/notes/${noteId}/comments`).catch(() => []);
      let followStatus = null;
      if (state.user && state.user.id !== detail.userId) {
        followStatus = await api.request(`/api/users/${detail.userId}/follow-status`).catch(() => null);
      }
      state.detail = detail;
      state.detailImages = images;
      state.comments = comments;
      state.followStatus = followStatus;
      state.replyTo = null;
      state.analysisDraft = null;
      renderDetail();
      $("#detailDrawer").classList.add("is-open");
      $("#detailDrawer").setAttribute("aria-hidden", "false");
      document.body.classList.add("drawer-open");
    } catch (error) { handleError(error); }
  }
  function closeDetail() {
    $("#detailDrawer").classList.remove("is-open");
    $("#detailDrawer").setAttribute("aria-hidden", "true");
    document.body.classList.remove("drawer-open");
    state.detail = null;
    state.detailImages = [];
    state.comments = [];
    state.replyTo = null;
    state.analysisDraft = null;
  }

  function canManageDetail() {
    return Boolean(state.detail && state.user && (state.user.id === state.detail.userId || state.user.role === 1));
  }

  function createActionButton(label, className, handler, disabled = false) {
    const button = el("button", className, label);
    button.type = "button";
    button.disabled = disabled;
    button.addEventListener("click", handler);
    return button;
  }

  function renderAiResult(result, canManage) {
    const card = el("div", "ai-result-card");
    card.append(el("h4", "", "已保存的 AI 阅读分析"), el("p", "", result.summary), el("p", "", `适宜人群：${result.audience}`));
    const scores = el("div", "score-row");
    scores.append(
      el("span", "score-chip", `完整性 ${result.completenessScore}`),
      el("span", "score-chip", `相关性 ${result.relevanceScore}`),
      el("span", "score-chip", `深度 ${result.depthScore}`)
    );
    card.append(scores);
    if (canManage) card.append(createActionButton("删除已保存结果", "quiet-button", deleteAiResult));
    return card;
  }

  function countComments(comments) {
    return comments.reduce((total, comment) => total + 1 + countComments(comment.replies || []), 0);
  }

  function createCommentNode(comment) {
    const node = el("article", "comment");
    const head = el("div", "comment-head");
    const identity = el("div");
    const avatar = el("span", "avatar");
    setAvatar(avatar, { nickname: comment.nickname, avatarUrl: comment.avatarUrl });
    identity.append(avatar, el("strong", "", comment.nickname || "阅读者"), el("time", "", formatDateTime(comment.createdAt)));
    head.append(identity);
    const tools = el("div", "comment-tools");
    tools.append(createActionButton("回复", "", () => { state.replyTo = comment.id; renderDetail(); }));
    if (state.user && (state.user.id === comment.userId || state.user.role === 1)) {
      tools.append(createActionButton("删除", "", () => deleteComment(comment.id)));
    }
    node.append(head, el("p", "comment-body", comment.content), tools);
    if (comment.replies && comment.replies.length) {
      const replies = el("div", "comment-replies");
      comment.replies.forEach((child) => replies.append(createCommentNode(child)));
      node.append(replies);
    }
    return node;
  }

  function renderDetailsComments() {
    const section = el("section", "detail-section");
    const head = el("div", "detail-section-head");
    head.append(el("h3", "", "评论与回复"), el("span", "status-badge", `${countComments(state.comments)} 条`));
    section.append(head);
    if (state.detail.visibility !== 1 || state.detail.auditStatus !== 1 || state.detail.status !== 1) {
      section.append(el("p", "loading-row", "只有审核通过的公开笔记可以评论。"));
      return section;
    }

    const form = el("form", "comment-form");
    const textarea = el("textarea");
    textarea.maxLength = 1000;
    textarea.required = true;
    textarea.placeholder = state.replyTo ? `正在回复评论 #${state.replyTo}` : "写下你的理解或问题";
    const formFoot = el("div", "form-actions");
    const submit = el("button", "primary-button", state.replyTo ? "发送回复" : "发表评论");
    submit.type = "submit";
    formFoot.append(submit);
    if (state.replyTo) {
      formFoot.append(createActionButton("取消回复", "quiet-button", () => { state.replyTo = null; renderDetail(); }));
    }
    form.append(textarea, formFoot);
    form.addEventListener("submit", async (event) => {
      event.preventDefault();
      const content = textarea.value.trim();
      if (!content) return;
      try {
        await api.request(`/api/notes/${state.detail.id}/comments`, {
          method: "POST", body: state.replyTo ? { content, parentId: state.replyTo } : { content }
        });
        state.replyTo = null;
        state.comments = await api.request(`/api/notes/${state.detail.id}/comments`);
        renderDetail();
        toast("评论已发布。");
      } catch (error) { handleError(error); }
    });
    section.append(form);

    const list = el("div", "comment-list");
    if (!state.comments.length) list.append(emptyState("还没有评论", "成为第一个留下阅读回应的人。"));
    else state.comments.forEach((comment) => list.append(createCommentNode(comment)));
    section.append(list);
    return section;
  }

  async function deleteComment(commentId) {
    if (!window.confirm("删除这条评论？其下所有回复也会一并删除。")) return;
    try {
      await api.request(`/api/notes/${state.detail.id}/comments/${commentId}`, { method: "DELETE" });
      state.comments = await api.request(`/api/notes/${state.detail.id}/comments`);
      renderDetail();
      toast("评论已删除。");
    } catch (error) { handleError(error); }
  }

  function renderImagesSection() {
    const section = el("section", "detail-section");
    const head = el("div", "detail-section-head");
    head.append(el("h3", "", "配图"), el("span", "status-badge", `${state.detailImages.length} 张`));
    section.append(head);
    if (state.detailImages.length) {
      const grid = el("div", "image-grid");
      state.detailImages.forEach((image, index) => {
        const card = el("figure", "detail-image-card");
        card.style.margin = "0";
        const picture = el("img");
        picture.src = image.imageUrl;
        picture.alt = `笔记配图 ${index + 1}`;
        card.append(picture);
        if (canManageDetail()) {
          const controls = el("div", "image-controls");
          controls.append(
            createActionButton("前移", "", () => moveImage(index, -1), index === 0),
            createActionButton("后移", "", () => moveImage(index, 1), index === state.detailImages.length - 1),
            createActionButton("删除", "", () => deleteImage(image))
          );
          card.append(controls);
        }
        grid.append(card);
      });
      section.append(grid);
    } else {
      section.append(el("p", "loading-row", "这篇笔记没有配图。"));
    }
    if (canManageDetail()) {
      const row = el("div", "upload-row");
      const input = el("input");
      input.type = "file";
      input.accept = ".jpg,.jpeg,.png,image/jpeg,image/png";
      input.multiple = true;
      input.id = "detailImageInput";
      row.append(input, createActionButton("上传图片", "secondary-button", uploadImages));
      section.append(row);
    }
    return section;
  }

  function renderAiSection() {
    const section = el("section", "detail-section");
    const head = el("div", "detail-section-head");
    head.append(el("h3", "", "AI 阅读辅助"), el("span", "status-badge", "可选功能"));
    section.append(head);
    if (state.detail.aiResult) section.append(renderAiResult(state.detail.aiResult, canManageDetail()));
    if (canManageDetail()) {
      const action = createActionButton(state.detail.aiResult ? "重新生成分析" : "生成 AI 阅读分析", "secondary-button", analyzeNote);
      action.style.marginTop = "0.8rem";
      section.append(action);
      if (state.analysisDraft) {
        const draft = el("div", "ai-draft-card");
        draft.append(el("h4", "", "这次分析尚未保存"), el("p", "", state.analysisDraft.summary), el("p", "", `适宜人群：${state.analysisDraft.audience}`));
        const scores = el("div", "score-row");
        scores.append(
          el("span", "score-chip", `完整性 ${state.analysisDraft.completenessScore}`),
          el("span", "score-chip", `相关性 ${state.analysisDraft.relevanceScore}`),
          el("span", "score-chip", `深度 ${state.analysisDraft.depthScore}`)
        );
        draft.append(scores);
        const actions = el("div", "detail-actions");
        actions.style.paddingBottom = "0";
        actions.append(
          createActionButton("保存结果", "primary-button", saveAiResult),
          createActionButton("丢弃", "quiet-button", () => { state.analysisDraft = null; renderDetail(); })
        );
        draft.append(actions);
        section.append(draft);
      }
    } else if (!state.detail.aiResult) {
      section.append(el("p", "loading-row", "作者尚未保存 AI 辅助结果。"));
    }
    return section;
  }

  function renderDetail() {
    const detail = state.detail;
    if (!detail) return;
    const container = $("#detailContent");
    container.replaceChildren();

    const head = el("header", "detail-head");
    const author = el("div", "detail-author");
    const avatar = el("span", "avatar avatar-large");
    setAvatar(avatar, { nickname: detail.authorNickname, avatarUrl: detail.authorAvatarUrl });
    const authorButton = el("button");
    authorButton.type = "button";
    authorButton.append(el("strong", "", detail.authorNickname || `作者 #${detail.userId}`), el("small", "", "查看作者主页"));
    authorButton.addEventListener("click", () => openAuthor(detail.userId));
    author.append(avatar, authorButton);
    head.append(author, el("h2", "", detail.title), el("div", "detail-meta", `${detail.bookName} · ${formatDate(detail.createdAt)} · 赞 ${detail.likeCount}`), badge(detail));
    if (detail.auditStatus === 2 && detail.auditRemark) head.append(el("p", "audit-note", `审核意见：${detail.auditRemark}`));
    container.append(head, el("div", "detail-body", detail.content));

    const actions = el("div", "detail-actions");
    actions.append(createActionButton(detail.likedByMe ? "取消点赞" : `点赞 ${detail.likeCount}`, "secondary-button", () => toggleLike(detail)));
    if (state.user && state.user.id !== detail.userId && state.followStatus) {
      actions.append(createActionButton(state.followStatus.following ? "已关注作者" : "关注作者", "secondary-button", () => toggleAuthorFollow(detail.userId)));
    }
    if (canManageDetail()) {
      actions.append(createActionButton("编辑", "secondary-button", () => {
        closeDetail();
        switchView("my");
        openComposer(detail);
      }));
      actions.append(createActionButton("删除", "secondary-button danger-button", () => deleteNote(detail.id)));
      if (detail.visibility === 1 && detail.auditStatus !== 1) {
        actions.append(createActionButton("提交审核", "primary-button", () => submitReview(detail.id)));
      }
    }
    if (state.user && state.user.role === 1 && detail.status === 1) {
      actions.append(createActionButton("下架笔记", "secondary-button danger-button", () => hideNote(detail.id)));
    }
    container.append(actions, renderImagesSection(), renderAiSection(), renderDetailsComments());
  }

  async function analyzeNote() {
    try {
      state.analysisDraft = await api.request(`/api/notes/${state.detail.id}/ai-analysis`, { method: "POST" });
      renderDetail();
      toast("AI 分析已生成，请选择保存或丢弃。");
    } catch (error) { handleError(error); }
  }

  async function saveAiResult() {
    if (!state.analysisDraft) return;
    try {
      await api.request(`/api/notes/${state.detail.id}/ai-result`, { method: "POST", body: state.analysisDraft });
      state.detail = await api.request(`/api/notes/${state.detail.id}`);
      state.analysisDraft = null;
      renderDetail();
      toast("AI 结果已保存到这篇笔记。");
    } catch (error) { handleError(error); }
  }

  async function deleteAiResult() {
    if (!window.confirm("删除这篇笔记已保存的 AI 结果？")) return;
    try {
      await api.request(`/api/notes/${state.detail.id}/ai-result`, { method: "DELETE" });
      state.detail = await api.request(`/api/notes/${state.detail.id}`);
      renderDetail();
      toast("AI 结果已删除。");
    } catch (error) { handleError(error); }
  }

  async function uploadImages() {
    const input = $("#detailImageInput");
    const files = input && input.files ? Array.from(input.files) : [];
    if (!files.length) { toast("请先选择图片。", true); return; }
    const data = new FormData();
    files.forEach((file) => data.append("files", file));
    try {
      await api.request(`/api/notes/${state.detail.id}/images`, { method: "POST", body: data });
      state.detailImages = await api.request(`/api/notes/${state.detail.id}/images`);
      renderDetail();
      toast(`已上传 ${files.length} 张图片。`);
    } catch (error) { handleError(error); }
  }

  async function deleteImage(image) {
    if (!window.confirm("删除这张配图？")) return;
    try {
      await api.request(`/api/notes/${state.detail.id}/images/${image.id}`, { method: "DELETE" });
      state.detailImages = await api.request(`/api/notes/${state.detail.id}/images`);
      renderDetail();
      toast("配图已删除。");
    } catch (error) { handleError(error); }
  }

  async function moveImage(index, delta) {
    const target = index + delta;
    if (target < 0 || target >= state.detailImages.length) return;
    const next = [...state.detailImages];
    [next[index], next[target]] = [next[target], next[index]];
    try {
      state.detailImages = await api.request(`/api/notes/${state.detail.id}/images/order`, {
        method: "PUT", body: { imageIds: next.map((image) => image.id) }
      });
      renderDetail();
      toast("图片顺序已更新。");
    } catch (error) { handleError(error); }
  }

  async function toggleAuthorFollow(userId) {
    const following = Boolean(state.followStatus && state.followStatus.following);
    try {
      state.followStatus = await api.request(`/api/users/${userId}/follow`, { method: following ? "DELETE" : "POST" });
      renderDetail();
      if (state.author && state.author.profile.id === userId) {
        state.author.follow = state.followStatus;
        renderAuthor();
      }
      toast(state.followStatus.following ? "已关注作者。" : "已取消关注。");
    } catch (error) { handleError(error); }
  }

  async function submitReview(noteId) {
    try {
      await api.request(`/api/notes/${noteId}/submit`, { method: "POST" });
      state.detail = await api.request(`/api/notes/${noteId}`);
      renderDetail();
      toast("已重新提交审核。");
    } catch (error) { handleError(error); }
  }

  async function hideNote(noteId) {
    if (!window.confirm("下架这篇公开笔记？")) return;
    try {
      await api.request(`/api/admin/notes/${noteId}/hide`, { method: "PUT" });
      state.detail = await api.request(`/api/notes/${noteId}`);
      renderDetail();
      toast("笔记已下架。");
    } catch (error) { handleError(error); }
  }

  async function deleteNote(noteId) {
    if (!window.confirm("删除这篇笔记？相关图片、点赞、评论和 AI 结果也会被清理。")) return;
    try {
      await api.request(`/api/notes/${noteId}`, { method: "DELETE" });
      closeDetail();
      await loadMy(1);
      toast("笔记已删除。");
    } catch (error) { handleError(error); }
  }
  async function openAuthor(userId) {
    $("#authorModal").hidden = false;
    $("#authorModalContent").replaceChildren(el("p", "loading-row", "正在打开作者主页…"));
    try {
      const [profile, stats, follow, notes] = await Promise.all([
        api.request(`/api/users/${userId}/profile`),
        api.request(`/api/users/${userId}/stats`),
        state.user && state.user.id !== userId ? api.request(`/api/users/${userId}/follow-status`) : Promise.resolve(null),
        api.request(`/api/users/${userId}/notes?page=1&size=50`)
      ]);
      state.author = { profile, stats, follow, notes };
      renderAuthor();
    } catch (error) {
      handleError(error);
      closeAuthor();
    }
  }

  function closeAuthor() {
    $("#authorModal").hidden = true;
    state.author = null;
  }

  function renderAuthor() {
    if (!state.author) return;
    const { profile, stats, follow, notes } = state.author;
    const container = $("#authorModalContent");
    container.replaceChildren();
    const hero = el("div", "author-hero");
    const main = el("div", "author-hero-main");
    const avatar = el("span", "avatar avatar-large");
    setAvatar(avatar, profile);
    const copy = el("div");
    copy.append(el("h2", "", profile.nickname || profile.username), el("p", "", `@${profile.username}`));
    main.append(avatar, copy);
    const action = el("div");
    if (state.user && state.user.id === profile.id) {
      action.append(el("span", "status-badge approved", "当前账号"));
    } else {
      const followButton = el("button", "primary-button", follow && follow.following ? "已关注" : "关注作者");
      followButton.type = "button";
      followButton.addEventListener("click", () => toggleAuthorFollow(profile.id));
      action.append(followButton);
    }
    hero.append(main, action);
    const statRow = el("div", "author-stat-row");
    statRow.append(
      el("span", "", `粉丝 ${stats.followerCount}`),
      el("span", "", `关注 ${stats.followingCount}`),
      el("span", "", `公开笔记 ${stats.publicNoteCount}`)
    );
    const noteHead = el("div", "list-heading");
    noteHead.append(el("h2", "", "公开笔记"), el("span", "", `共 ${notes.total} 篇`));
    const noteList = el("div", "note-list author-notes");
    if (!notes.records.length) noteList.append(emptyState("还没有公开笔记", "作者通过审核的笔记会出现在这里。"));
    else notes.records.forEach((note) => noteList.append(createNoteRow(note, "author")));
    container.append(hero, statRow, noteHead, noteList);
  }

  function openProfile() {
    if (!state.user) { showAuth(); return; }
    renderUser(state.user);
    $("#profileModal").hidden = false;
  }

  async function handleProfileSubmit(event) {
    event.preventDefault();
    try {
      const updated = await api.request("/api/users/me", { method: "PUT", body: { nickname: $("#profileNickname").value.trim() } });
      renderUser(updated);
      toast("昵称已更新。");
    } catch (error) { handleError(error); }
  }

  async function handleAvatarSubmit(event) {
    event.preventDefault();
    const file = $("#avatarInput").files[0];
    if (!file) { toast("请先选择头像图片。", true); return; }
    const data = new FormData();
    data.append("file", file);
    try {
      const updated = await api.request("/api/users/me/avatar", { method: "POST", body: data });
      renderUser(updated);
      toast("头像已更新。");
    } catch (error) { handleError(error); }
  }

  async function loadConversations(selectId = null) {
    try {
      const data = await api.request("/api/ai/conversations?page=1&size=50");
      state.conversations = data.records;
      const target = selectId || state.currentConversationId || (data.records[0] && data.records[0].id);
      state.currentConversationId = target || null;
      renderConversations();
      if (target) await selectConversation(target);
      else renderChat(null, []);
    } catch (error) { handleError(error); }
  }

  function renderConversations() {
    const list = $("#conversationList");
    list.replaceChildren();
    if (!state.conversations.length) {
      list.append(emptyState("还没有会话", "新建会话后可以向 AI 客服询问平台用法。"));
      return;
    }
    state.conversations.forEach((conversation) => {
      const button = el("button", `conversation-item${conversation.id === state.currentConversationId ? " is-active" : ""}`);
      button.type = "button";
      button.append(el("strong", "", conversation.title), el("small", "", formatDateTime(conversation.updatedAt)));
      button.addEventListener("click", () => selectConversation(conversation.id));
      list.append(button);
    });
  }

  async function selectConversation(conversationId) {
    try {
      const detail = await api.request(`/api/ai/conversations/${conversationId}`);
      state.currentConversationId = conversationId;
      renderConversations();
      renderChat(detail.conversation, detail.messages);
    } catch (error) {
      handleError(error);
      if (error.status === 403 || error.status === 404) {
        state.currentConversationId = null;
        await loadConversations();
      }
    }
  }

  function renderChat(conversation, messages) {
    $("#chatTitle").textContent = conversation ? conversation.title : "选择或新建一个会话";
    $("#deleteConversation").hidden = !conversation;
    const box = $("#chatMessages");
    box.replaceChildren();
    if (!conversation) box.append(emptyState("可以问什么？", "公开笔记审核、文件上传、点赞评论、账号问题、AI 阅读辅助。"));
    else if (!messages.length) box.append(emptyState("会话已建立", "输入第一个问题开始咨询。"));
    else messages.forEach((message) => {
      const bubble = el("div", `chat-message ${message.role === "USER" ? "user" : "assistant"}`, message.content);
      bubble.append(el("time", "", formatDateTime(message.createdAt)));
      box.append(bubble);
    });
    const input = $("#chatInput");
    const submit = $("#chatForm button[type=submit]");
    input.disabled = !conversation;
    submit.disabled = !conversation;
    box.scrollTop = box.scrollHeight;
  }

  async function createConversation() {
    try {
      const conversation = await api.request("/api/ai/conversations", { method: "POST", body: {} });
      state.currentConversationId = conversation.id;
      await loadConversations(conversation.id);
      toast("新会话已建立。");
      $("#chatInput").focus();
    } catch (error) { handleError(error); }
  }

  async function deleteConversation() {
    if (!state.currentConversationId || !window.confirm("删除这个会话？消息记录也会一并清理。")) return;
    try {
      await api.request(`/api/ai/conversations/${state.currentConversationId}`, { method: "DELETE" });
      state.currentConversationId = null;
      await loadConversations();
      toast("会话已删除。");
    } catch (error) { handleError(error); }
  }

  async function handleChatSubmit(event) {
    event.preventDefault();
    const content = $("#chatInput").value.trim();
    if (!content || !state.currentConversationId) return;
    const input = $("#chatInput");
    const box = $("#chatMessages");
    const typing = el("div", "chat-typing", "AI 客服正在整理回答…");
    box.append(typing);
    box.scrollTop = box.scrollHeight;
    input.disabled = true;
    try {
      await api.request(`/api/ai/conversations/${state.currentConversationId}/messages`, { method: "POST", body: { content } });
      input.value = "";
      await selectConversation(state.currentConversationId);
    } catch (error) {
      typing.remove();
      handleError(error);
    } finally {
      input.disabled = false;
      input.focus();
    }
  }
  function renderAdminNotes(records, mode) {
    if (!records.length) return emptyState("这里没有笔记", mode === "review" ? "当前没有待审核内容。" : "当前没有符合条件的公开笔记。");
    const table = el("table", "admin-table");
    const head = el("thead");
    const headRow = el("tr");
    ["笔记", "作者", "状态", "点赞", "操作"].forEach((text) => headRow.append(el("th", "", text)));
    head.append(headRow);
    const body = el("tbody");
    records.forEach((note) => {
      const row = el("tr");
      const titleCell = el("td");
      titleCell.append(el("strong", "", note.title), el("small", "", note.bookName));
      const authorCell = el("td", "", note.authorNickname || `用户 #${note.userId}`);
      const statusCell = el("td");
      statusCell.append(badge(note));
      const actionCell = el("td");
      const actions = el("div", "table-actions");
      if (mode === "review" || (mode === "notes" && note.auditStatus !== 1)) {
        actions.append(createActionButton("通过", "", () => adminReview(note.id, "approve")));
        actions.append(createActionButton("驳回", "danger", () => adminReject(note.id)));
      }
      actions.append(createActionButton("查看", "", () => openDetail(note.id)));
      if (mode === "notes" && note.status === 1) {
        actions.append(createActionButton("下架", "danger", () => adminReview(note.id, "hide")));
      }
      actionCell.append(actions);
      row.append(titleCell, authorCell, statusCell, el("td", "", String(note.likeCount || 0)), actionCell);
      body.append(row);
    });
    table.append(head, body);
    return table;
  }

  async function adminReview(noteId, action) {
    if (action === "hide" && !window.confirm("下架这篇公开笔记？")) return;
    try {
      await api.request(`/api/admin/notes/${noteId}/${action}`, { method: "PUT" });
      toast(action === "approve" ? "笔记已通过审核。" : "笔记已下架。");
      await loadAdmin();
    } catch (error) { handleError(error); }
  }

  async function adminReject(noteId) {
    const remark = window.prompt("请输入驳回意见：", "请补充内容或调整公开范围。");
    if (remark === null) return;
    try {
      await api.request(`/api/admin/notes/${noteId}/reject`, { method: "PUT", body: { remark } });
      toast("笔记已驳回。");
      await loadAdmin();
    } catch (error) { handleError(error); }
  }

  function renderAdminUsers(records) {
    if (!records.length) return emptyState("没有用户", "当前没有可管理的账号。");
    const table = el("table", "admin-table");
    const head = el("thead");
    const row = el("tr");
    ["用户", "角色", "状态", "注册时间", "操作"].forEach((text) => row.append(el("th", "", text)));
    head.append(row);
    const body = el("tbody");
    records.forEach((user) => {
      const tr = el("tr");
      const userCell = el("td");
      userCell.append(el("strong", "", user.nickname || user.username), el("small", "", `@${user.username}`));
      const statusCell = el("td");
      statusCell.append(el("span", `status-badge ${user.status === 1 ? "approved" : "hidden"}`, user.status === 1 ? "正常" : "禁用"));
      const actionCell = el("td");
      const actions = el("div", "table-actions");
      if (!(state.user && state.user.id === user.id && user.status === 1)) {
        actions.append(createActionButton(
          user.status === 1 ? "禁用" : "启用",
          user.status === 1 ? "danger" : "",
          () => updateUserStatus(user.id, user.status === 1 ? 0 : 1)
        ));
      }
      actionCell.append(actions);
      tr.append(userCell, el("td", "", user.role === 1 ? "管理员" : "普通用户"), statusCell, el("td", "", formatDate(user.createdAt)), actionCell);
      body.append(tr);
    });
    table.append(head, body);
    return table;
  }

  async function updateUserStatus(userId, status) {
    if (status === 0 && !window.confirm("禁用该账号？已有登录凭证会立即失效。")) return;
    try {
      await api.request(`/api/admin/users/${userId}/status`, { method: "PUT", body: { status } });
      toast(status === 1 ? "账号已启用。" : "账号已禁用。");
      await loadAdmin();
    } catch (error) { handleError(error); }
  }

  async function loadAdmin() {
    const container = $("#adminContent");
    container.replaceChildren(el("p", "loading-row", "正在加载管理数据…"));
    try {
      if (state.adminTab === "users") {
        const data = await api.request("/api/admin/users?page=1&size=50");
        container.replaceChildren(renderAdminUsers(data.records));
      } else if (state.adminTab === "stats") {
        const stats = await api.request("/api/admin/ai/stats");
        const grid = el("div", "stats-grid");
        [["总调用", stats.total], ["成功", stats.success], ["失败", stats.failure]].forEach(([label, value]) => {
          const card = el("div", "stat-card");
          card.append(el("span", "", label), el("strong", "", String(value)));
          grid.append(card);
        });
        container.replaceChildren(grid);
      } else if (state.adminTab === "review") {
        const data = await api.request("/api/admin/notes/pending?page=1&size=50");
        container.replaceChildren(renderAdminNotes(data.records, "review"));
      } else {
        const data = await api.request("/api/admin/notes?page=1&size=50");
        container.replaceChildren(renderAdminNotes(data.records, "notes"));
      }
    } catch (error) { handleError(error); }
  }

  function switchAdminTab(tab) {
    state.adminTab = tab;
    document.querySelectorAll("[data-admin-tab]").forEach((button) => button.classList.toggle("is-active", button.dataset.adminTab === tab));
    loadAdmin();
  }

  async function loadView(view) {
    if (view === "explore") return loadExplore(state.page);
    if (view === "my") return loadMy(1);
    if (view === "liked") return loadLiked(1);
    if (view === "ranking") return loadRanking(1);
    if (view === "authors") return loadAuthors();
    if (view === "assistant") return loadConversations();
    if (view === "admin") return loadAdmin();
  }

  async function switchView(view) {
    if (!state.user) { showAuth(); return; }
    if (view === "admin" && state.user.role !== 1) { toast("需要管理员权限。", true); return; }
    state.view = view;
    state.page = 1;
    $("#crumb").textContent = viewLabels[view] || "阅读工作台";
    document.querySelectorAll(".nav-item").forEach((button) => button.classList.toggle("is-active", button.dataset.view === view));
    ["explore", "my", "liked", "ranking", "authors", "assistant", "admin"].forEach((name) => {
      $(`#${name}View`).hidden = name !== view;
    });
    window.scrollTo({ top: 0, behavior: "smooth" });
    await loadView(view);
  }

  function bindEvents() {
    document.querySelectorAll("[data-auth-mode]").forEach((button) => button.addEventListener("click", () => switchAuthMode(button.dataset.authMode)));
    $("#loginForm").addEventListener("submit", handleLogin);
    $("#registerForm").addEventListener("submit", handleRegister);
    $("#logoutButton").addEventListener("click", logout);
    $("#profileButton").addEventListener("click", openProfile);
    $("#mobileProfileButton").addEventListener("click", openProfile);
    $("#profileForm").addEventListener("submit", handleProfileSubmit);
    $("#avatarForm").addEventListener("submit", handleAvatarSubmit);
    document.querySelectorAll("[data-close-profile]").forEach((node) => node.addEventListener("click", () => { $("#profileModal").hidden = true; }));
    document.querySelectorAll("[data-close-detail]").forEach((node) => node.addEventListener("click", closeDetail));
    document.querySelectorAll("[data-close-author]").forEach((node) => node.addEventListener("click", closeAuthor));
    document.querySelectorAll(".nav-item").forEach((button) => button.addEventListener("click", () => switchView(button.dataset.view)));
    document.querySelectorAll("[data-admin-tab]").forEach((button) => button.addEventListener("click", () => switchAdminTab(button.dataset.adminTab)));
    $("#brandHome").addEventListener("click", (event) => { event.preventDefault(); switchView("explore"); });
    $("#searchForm").addEventListener("submit", (event) => {
      event.preventDefault();
      state.keyword = $("#searchInput").value.trim();
      loadExplore(1);
    });
    $("#clearSearch").addEventListener("click", () => {
      state.keyword = "";
      $("#searchInput").value = "";
      loadExplore(1);
    });
    $("#toggleComposer").addEventListener("click", () => {
      const form = $("#noteForm");
      if (!form.hidden && !state.editingNoteId) {
        form.hidden = true;
        $("#toggleComposer").textContent = "写一篇笔记";
      } else {
        openComposer();
      }
    });
    $("#cancelEdit").addEventListener("click", () => {
      resetComposer();
      $("#noteForm").hidden = true;
    });
    $("#noteForm").addEventListener("submit", handleNoteSubmit);
    $("#newConversation").addEventListener("click", createConversation);
    $("#deleteConversation").addEventListener("click", deleteConversation);
    $("#chatForm").addEventListener("submit", handleChatSubmit);
    $("#chatInput").addEventListener("keydown", (event) => {
      if (event.key === "Enter" && !event.shiftKey) {
        event.preventDefault();
        $("#chatForm").requestSubmit();
      }
    });
    document.addEventListener("keydown", (event) => {
      if (event.key !== "Escape") return;
      if ($("#detailDrawer").classList.contains("is-open")) closeDetail();
      else if (!$("#authorModal").hidden) closeAuthor();
      else if (!$("#profileModal").hidden) $("#profileModal").hidden = true;
    });
  }

  async function init() {
    bindEvents();
    if (!api.getToken()) { showAuth(); return; }
    try {
      const user = await api.request("/api/users/me");
      enterApp(user);
    } catch (error) {
      api.clearToken();
      showAuth();
    }
  }

  init();
})();