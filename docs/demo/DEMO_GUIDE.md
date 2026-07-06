# CTMS Demo Guide + Speaker Notes

**Target length:** 10–15 minutes
**Audience:** 5-person team demo
**Prep:** Run `docs/demo/demo_seed.sql` against the target database (see header comment in that file for prerequisites), then confirm both backend and frontend are up.

---

## 0. Before You Start (2 minutes, not counted in the 10–15)

1. Confirm the backend is reachable and the frontend loads.
2. Log in once as `demo.mgr` / `Demo2026!Pass` to confirm the seed data is there — you should see the study **"Oncology Combination Therapy Trial"** with 5 subjects across 2 sites.
3. Open 2 browser windows/profiles side by side if one person is driving: one logged in as `demo.mgr` (or `demo.coord`), one logged in as `demo.auditor` — saves time switching accounts mid-demo.
4. Decide who's driving. If splitting across the 5 people, a natural split is: **Presenter A** — intro + study/site/subject tour; **Presenter B** — consent gate + adverse event; **Presenter C** — financial (payment hold/release); **Presenter D** — protocol deviation + site activation; **Presenter E** — traceability report + close. One person can just as easily run the whole thing solo — the notes below work either way.

**Demo accounts** (all password `Demo2026!Pass`):

| Username | Role | Use for |
|---|---|---|
| `demo.mgr` | Study Manager | Study/site setup, protocol deviations |
| `demo.coord` | Site Coordinator | Subject enrollment, visits, consent upload, withdrawal |
| `demo.investigator` | Investigator | Adverse event review/resolution |
| `demo.finance` | Finance Manager | Budget, payment hold/release |
| `demo.auditor` | QA/Compliance Auditor | Audit log, traceability report |
| `demo.admin` | Admin | Anything, fallback |
| `demo.cra` | CRA/Monitor | (not needed in this script, available if asked) |

---

## 1. Opening — Positioning (≈1 min)

**Say:**
> "This is our Clinical Trial Management System — CTMS. The one thing I want you to take away today: every decision this system makes is a visible, traceable rule — there's no AI black box making eligibility, scheduling, or payment decisions. That's a deliberate design choice, because in a regulated clinical-trial environment, every action needs to be explainable and auditable. I'll show you a live trial with real subjects, walk through a few of the automated workflows, and end with something we're proud of — a full compliance traceability report that reconstructs an entire audit trail on demand."

Log in as **`demo.mgr`**. Land on the dashboard — briefly point out the portfolio-level view (active studies, enrollment, open tasks) before drilling in.

---

## 2. Study → Site → Subject Tour (≈2 min)

Navigate to **Oncology Combination Therapy Trial**.

**Say:**
> "This study is in the Conduct phase — meaning it's actively enrolling and treating subjects. It has two sites."

Open **Metro General Hospital** (ACTIVE). Show the activation checklist — all 5 items complete, activated with an e-signature.

Navigate to the subject list. Point out the variety:
> "Maria Alvarez is in active treatment — she's been through screening and her first treatment visit already. Robert Kim was just screened. Aisha Bello is enrolled. James Whitfield has some history I'll come back to."

Open **Maria Alvarez**'s subject detail page. Show her visit schedule (2 completed, 1 upcoming) and her status history. This is the "everything working as intended" reference case — don't linger, just establish the shape of the data.

---

## 3. Live Demo: Consent Gate (≈2.5 min)

This is a GCP-compliance feature — the system will not let a visit be marked complete unless a signed informed-consent document is on file for *that specific subject*.

Switch to **`demo.coord`**. Open **Robert Kim**'s subject page.

1. Try to mark his Screening Visit **Complete**.
2. **Say:** "Watch what happens — he was screened, but there's no consent document on file yet."
3. It should reject with a clear error: *no CURRENT informed consent document on file*.
4. Upload a document for Robert Kim: category **Informed Consent**, any file.
5. Retry marking the visit complete — it now succeeds.

**Say:**
> "That check is scoped per-subject, not per-study — uploading one subject's consent never accidentally clears the gate for anyone else in the same study. That distinction actually came out of a real design review we did."

---

## 4. Live Demo: Adverse Event + Rules-Driven Escalation (≈2.5 min)

Still as `demo.coord` (or switch to `demo.investigator` if convenient). On **Maria Alvarez**'s page:

1. Click **Report Adverse Event**. Description: *"Severe allergic reaction after second dose"*. Severity: **SEVERE**.
2. Submit.
3. **Say:** "Watch the Tasks area — I didn't manually assign anyone."
4. Show that a high-priority escalation task was auto-created (owner = the coordinator, escalation target = the study manager) — this fired from a Drools rule, not a hardcoded `if` in the code.
5. Switch to `demo.investigator`. Transition the AE to **Under Review**, then click **Resolve**.
6. Type the wrong password first — **show the rejection** ("Incorrect password. Please try again.").
7. Enter the correct password (`Demo2026!Pass`) with resolution notes — it succeeds.

**Say:**
> "Resolving a safety event requires password re-authentication, same as a physical signature would in a paper process — that's our 21 CFR Part 11 e-signature mechanism. If the password's wrong, nothing about the record changes."

---

## 5. Live Demo: Financial — Payment Hold/Release (≈2 min)

Switch to **`demo.finance`**. Open the **Payments** list, filtered to this study.

**Say:**
> "Every completed visit that matches a payment-trigger rule automatically generates a payment — no manual invoicing. You can see one that's already been released, and one still pending."

1. Open the **PENDING** payment (tied to Maria Alvarez's Treatment Visit 1).
2. Click **Hold**, give a reason ("awaiting site invoice confirmation").
3. Click **Release** — try the wrong password once, then the correct one.

**Say:**
> "Same e-signature pattern as the adverse event — release is gated the same way payment approval would be gated on paper."

---

## 6. Quick Hits: Protocol Deviation + Site Activation E-Signature (≈1.5 min)

Switch to **`demo.mgr`**.

1. Open **James Whitfield** — point out he already has a logged protocol deviation ("Treatment Visit 1 window missed by 4 days") — explain this is a simple permanent record, not a workflow like the adverse event (deviations are logged, not "resolved").
2. Optionally report a new one live on any subject to show the form.

Then open **Riverside Regional Medical Center** (the second site — still **Pending Activation** even though its checklist is fully complete).

**Say:**
> "Every prerequisite here is done, but activating a site is a go-live, compliance-gated action, so it needs the same password re-authentication."

Click **Attempt Activation** → the dialog asks for password + reason → confirm → site flips to **ACTIVE**.

---

## 7. Closing: Traceability Report (≈1.5 min — the payoff moment)

Switch to **`demo.auditor`**. Go to the **Audit Log** admin page.

Type entity name **`AdverseEvent`** and the ID of the adverse event you just resolved live in step 4 (or use James Whitfield's historical one if you skipped ahead). Click **View Traceability**.

**Say:**
> "This is what an inspector or auditor sees: the complete history of one record — every audit entry, every e-signature, who did what and when — reconstructed on demand. Nothing here was written by hand for this demo; it's the same mechanism generating every entry you've just watched happen live."

Point out the CSV export button on the main audit log screen as well — same underlying data, exportable for offline review.

---

## 8. (Optional, if time remains) Patient Portal Glimpse (≈1 min)

If you have 1–2 minutes left: mention (don't necessarily log in) that subjects also get their own lightweight portal — visit schedule, document upload, profile, and self-service adverse-event reporting — reusing every mechanism just demoed underneath, just presented simply for a non-technical audience.

---

## 9. Wrap-Up (≈30 sec)

**Say:**
> "Everything you saw — the consent gate, the escalation task, the payment, the e-signatures — is a configured, explainable rule, not a model prediction. And the traceability report proves it after the fact. Happy to take questions."

---

## Timing Cheat Sheet

| Section | Target time | Running total |
|---|---|---|
| Opening | 1:00 | 1:00 |
| Study/Site/Subject tour | 2:00 | 3:00 |
| Consent gate | 2:30 | 5:30 |
| Adverse event + escalation | 2:30 | 8:00 |
| Payment hold/release | 2:00 | 10:00 |
| Protocol deviation + site activation | 1:30 | 11:30 |
| Traceability report | 1:30 | 13:00 |
| (Optional) Patient portal | 1:00 | 14:00 |
| Wrap-up | 0:30 | 14:30 |

Cut the optional patient-portal beat first if you're running long; everything else is core.

## If Something Goes Wrong

- **Consent-gate demo doesn't block**: someone may have already uploaded a consent document for Robert Kim in an earlier rehearsal — check the Documents tab first, or pick a different SCREENED subject.
- **Wrong-password step "succeeds" instead of rejecting**: double check you actually typed a *wrong* password — the system doesn't fake this, it's a real check.
- **Site 2 auto-activates before you click "Attempt Activation"**: this can only happen if someone touched its checklist items through the UI after seeding (which would silently complete the activation) — don't edit Riverside's checklist before the demo.
- **Traceability report looks empty**: you're pointed at an entity ID from a different database than the one you seeded, or one that hasn't had anything logged yet — use one of the entities acted on earlier in the demo (the AE from step 4, or Payment from step 5).
