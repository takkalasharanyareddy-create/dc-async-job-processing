(() => {
  const UI_CONFIG_PATH = "/api/ui-config";

  const form = document.getElementById("jobForm");
  const nameInput = document.getElementById("jobName");
  const detailsInput = document.getElementById("jobDetails");
  const submitBtn = document.getElementById("submitBtn");
  const formMessage = document.getElementById("formMessage");
  const statusFilter = document.getElementById("statusFilter");
  const dashboardState = document.getElementById("dashboardState");
  const stateMessage = document.getElementById("stateMessage");
  const tableWrap = document.getElementById("tableWrap");
  const jobsBody = document.getElementById("jobsBody");
  const livePill = document.getElementById("livePill");
  const liveLabel = document.getElementById("liveLabel");

  // Fixed defaults (stream path does not change): /api/jobs + /stream
  let config = {
    jobsBasePath: "/api/jobs",
    jobsStreamPath: "/api/jobs/stream",
  };
  let eventSource = null;
  let hasReceivedSnapshot = false;

  function setLiveState(state, label) {
    livePill.dataset.state = state;
    liveLabel.textContent = label;
  }

  function setDashboardState(state, message) {
    dashboardState.hidden = false;
    dashboardState.dataset.state = state;
    stateMessage.textContent = message;
    tableWrap.hidden = state !== "ready";
  }

  function escapeHtml(value) {
    return String(value ?? "")
      .replaceAll("&", "&amp;")
      .replaceAll("<", "&lt;")
      .replaceAll(">", "&gt;")
      .replaceAll('"', "&quot;");
  }

  function parseJobFields(job) {
    let details = "";
    if (job.payload) {
      try {
        const parsed = JSON.parse(job.payload);
        if (parsed && typeof parsed === "object") {
          details = parsed.details || "";
        } else {
          details = job.payload;
        }
      } catch {
        details = job.payload;
      }
    }
    return {
      name: job.name || "Untitled job",
      details,
    };
  }

  function formatTime(value) {
    if (!value) return "—";
    try {
      return new Date(value).toLocaleString();
    } catch {
      return value;
    }
  }

  function renderJobs(jobs) {
    hasReceivedSnapshot = true;

    if (!Array.isArray(jobs) || jobs.length === 0) {
      jobsBody.innerHTML = "";
      setDashboardState("empty", "No jobs for this filter yet. Submit one to get started.");
      return;
    }

    jobsBody.innerHTML = jobs
      .map((job) => {
        const { name, details } = parseJobFields(job);
        const result = job.result || job.error || "—";
        return `
          <tr>
            <td class="job-name">${escapeHtml(name)}</td>
            <td class="job-details">${escapeHtml(details)}</td>
            <td>
              <span class="badge ${escapeHtml(job.status)}">${escapeHtml(job.status)}</span>
              <div class="mono">try ${escapeHtml(job.attemptCount ?? 0)}/${escapeHtml(job.maxAttempts ?? 3)}</div>
            </td>
            <td class="mono">${escapeHtml(formatTime(job.createdAt))}</td>
            <td class="mono">${escapeHtml(formatTime(job.updatedAt))}</td>
            <td class="job-details">${escapeHtml(result)}</td>
          </tr>
        `;
      })
      .join("");

    setDashboardState("ready", "");
    dashboardState.hidden = true;
  }

  function streamUrl() {
    const status = statusFilter.value;
    const url = new URL(config.jobsStreamPath, window.location.origin);
    if (status) {
      url.searchParams.set("status", status);
    }
    return url.toString();
  }

  function connectSse() {
    if (eventSource) {
      eventSource.close();
      eventSource = null;
    }

    hasReceivedSnapshot = false;
    setLiveState("loading", "Connecting…");
    setDashboardState("loading", "Loading jobs…");

    eventSource = new EventSource(streamUrl());

    eventSource.addEventListener("connected", () => {
      setLiveState("live", "Live");
    });

    eventSource.addEventListener("snapshot", (event) => {
      try {
        const jobs = JSON.parse(event.data);
        setLiveState("live", "Live");
        renderJobs(jobs);
      } catch (err) {
        setDashboardState("error", "Failed to parse live update.");
        setLiveState("error", "Stream error");
      }
    });

    eventSource.addEventListener("heartbeat", () => {
      if (livePill.dataset.state !== "error") {
        setLiveState("live", "Live");
      }
    });

    eventSource.onerror = () => {
      setLiveState("reconnecting", "Reconnecting…");
      if (!hasReceivedSnapshot) {
        setDashboardState("error", "Could not connect to live updates. Retrying…");
      }
    };
  }

  async function loadConfig() {
    const response = await fetch(UI_CONFIG_PATH);
    if (!response.ok) {
      throw new Error("Unable to load UI config");
    }
    config = await response.json();
  }

  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    formMessage.textContent = "";
    formMessage.className = "form-message";

    const name = nameInput.value.trim();
    const details = detailsInput.value.trim();
    if (!name || !details) {
      formMessage.textContent = "Please fill in job name and details.";
      formMessage.classList.add("error");
      return;
    }

    submitBtn.disabled = true;
    formMessage.textContent = "Submitting…";

    try {
      const response = await fetch(config.jobsBasePath, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ name, details }),
      });

      if (!response.ok) {
        let message = `Request failed (${response.status})`;
        try {
          const errBody = await response.json();
          message = errBody.message || message;
        } catch {
          // keep default message
        }
        throw new Error(message);
      }

      form.reset();
      formMessage.textContent = "Job created. Dashboard will update live.";
      formMessage.classList.add("success");
    } catch (err) {
      formMessage.textContent = err.message || "Failed to create job.";
      formMessage.classList.add("error");
    } finally {
      submitBtn.disabled = false;
    }
  });

  statusFilter.addEventListener("change", () => {
    connectSse();
  });

  loadConfig()
    .then(() => connectSse())
    .catch((err) => {
      setLiveState("error", "Config error");
      setDashboardState("error", err.message || "Failed to start dashboard.");
    });
})();
