// Small progressive enhancements. Every rule here is also enforced by the server,
// so the app works the same way with JavaScript turned off.
document.addEventListener("DOMContentLoaded", () => {
  // Move focus to the error summary so keyboard and screen-reader users hear what went wrong.
  const summary = document.getElementById("error-summary");
  if (summary) summary.focus();

  const form = document.querySelector("[data-ticket-form]");
  if (!form) return;
  const status = form.querySelector("#status");
  const technician = form.querySelector("#assignee");
  const resolution = form.querySelector("#resolution");
  const message = form.querySelector("[data-sync-message]");
  const hasOption = (select, value) => [...select.options].some((o) => o.value === value);
  const say = (text) => { if (message) message.textContent = text; };

  // The assignment rule: Open means no technician; every other status needs one.
  technician.addEventListener("change", () => {
    if (status.value === "Open" && technician.value !== "" && hasOption(status, "Assigned")) {
      status.value = "Assigned";
      say("Status changed to Assigned, because a ticket with a technician can't stay Open.");
    } else {
      say("");
    }
  });
  status.addEventListener("change", () => {
    if (status.value === "Open" && technician.value !== "") {
      technician.value = "";
      say("Technician cleared, because an Open ticket goes back to the queue unassigned.");
    } else if (status.value !== "Open" && technician.value === "") {
      say("Choose a technician before saving.");
    } else {
      say("");
    }
    const finished = status.value === "Resolved" || status.value === "Closed";
    resolution.required = finished;
  });
  resolution.required = status.value === "Resolved" || status.value === "Closed";
});
