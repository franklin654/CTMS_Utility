import { Routes } from '@angular/router';
import { authGuard, roleGuard } from './core/auth/auth.guard';
import { ShellComponent } from './layout/shell/shell.component';
import { PatientShellComponent } from './layout/patient-shell/patient-shell.component';

export const routes: Routes = [
  {
    path: 'login',
    loadComponent: () => import('./features/auth/login/login.component').then((m) => m.LoginComponent),
  },
  {
    path: 'forgot-password',
    loadComponent: () =>
      import('./features/auth/forgot-password/forgot-password.component').then((m) => m.ForgotPasswordComponent),
  },
  {
    path: 'reset-password',
    loadComponent: () =>
      import('./features/auth/reset-password/reset-password.component').then((m) => m.ResetPasswordComponent),
  },
  {
    path: 'change-password',
    loadComponent: () =>
      import('./features/auth/change-password/change-password.component').then((m) => m.ChangePasswordComponent),
  },
  {
    path: '',
    component: ShellComponent,
    canActivate: [authGuard],
    children: [
      {
        path: 'dashboard',
        loadComponent: () => import('./features/dashboard/dashboard.component').then((m) => m.DashboardComponent),
      },
      {
        path: 'studies',
        loadComponent: () =>
          import('./features/studies/study-list/study-list.component').then((m) => m.StudyListComponent),
      },
      {
        path: 'studies/new',
        canActivate: [roleGuard(['STUDY_MANAGER', 'ADMIN'])],
        loadComponent: () =>
          import('./features/studies/study-create/study-create.component').then((m) => m.StudyCreateComponent),
      },
      {
        path: 'studies/:id',
        loadComponent: () =>
          import('./features/studies/study-detail/study-detail.component').then((m) => m.StudyDetailComponent),
      },
      {
        path: 'sites',
        loadComponent: () => import('./features/sites/site-list/site-list.component').then((m) => m.SiteListComponent),
      },
      {
        path: 'sites/new',
        canActivate: [roleGuard(['STUDY_MANAGER', 'ADMIN'])],
        loadComponent: () =>
          import('./features/sites/site-create/site-create.component').then((m) => m.SiteCreateComponent),
      },
      {
        path: 'sites/:id',
        loadComponent: () =>
          import('./features/sites/site-detail/site-detail.component').then((m) => m.SiteDetailComponent),
      },
      {
        path: 'subjects',
        loadComponent: () =>
          import('./features/subjects/subject-list/subject-list.component').then((m) => m.SubjectListComponent),
      },
      {
        path: 'subjects/new',
        canActivate: [roleGuard(['SITE_COORDINATOR', 'STUDY_MANAGER', 'ADMIN'])],
        loadComponent: () =>
          import('./features/subjects/subject-enroll/subject-enroll.component').then((m) => m.SubjectEnrollComponent),
      },
      {
        path: 'subjects/:id',
        loadComponent: () =>
          import('./features/subjects/subject-detail/subject-detail.component').then((m) => m.SubjectDetailComponent),
      },
      {
        path: 'studies/:studyId/eligibility-criteria',
        canActivate: [roleGuard(['STUDY_MANAGER', 'ADMIN'])],
        loadComponent: () =>
          import('./features/eligibility-criteria/eligibility-criteria.component').then(
            (m) => m.EligibilityCriteriaComponent,
          ),
      },
      {
        path: 'studies/:studyId/visit-templates',
        canActivate: [roleGuard(['STUDY_MANAGER', 'ADMIN'])],
        loadComponent: () =>
          import('./features/visit-templates/visit-templates.component').then((m) => m.VisitTemplatesComponent),
      },
      {
        path: 'studies/:studyId/milestones',
        loadComponent: () =>
          import('./features/milestones/milestones.component').then((m) => m.MilestonesComponent),
      },
      {
        path: 'studies/:studyId/document-requirements',
        loadComponent: () =>
          import('./features/document-requirements/document-requirements.component').then(
            (m) => m.DocumentRequirementsComponent,
          ),
      },
      {
        path: 'admin/users',
        canActivate: [roleGuard(['ADMIN'])],
        loadComponent: () =>
          import('./features/admin/users/user-list/user-list.component').then((m) => m.UserListComponent),
      },
      {
        path: 'admin/rule-sets',
        canActivate: [roleGuard(['ADMIN'])],
        loadComponent: () =>
          import('./features/admin/rule-sets/rule-set-list/rule-set-list.component').then((m) => m.RuleSetListComponent),
      },
      {
        path: 'admin/rule-sets/:name',
        canActivate: [roleGuard(['ADMIN'])],
        loadComponent: () =>
          import('./features/admin/rule-sets/rule-set-detail/rule-set-detail.component').then(
            (m) => m.RuleSetDetailComponent,
          ),
      },
      {
        path: 'studies/:studyId/budget',
        canActivate: [roleGuard(['FINANCE_MANAGER', 'ADMIN'])],
        loadComponent: () =>
          import('./features/finance/budget-detail/budget-detail.component').then((m) => m.BudgetDetailComponent),
      },
      {
        path: 'payments',
        canActivate: [roleGuard(['FINANCE_MANAGER', 'ADMIN'])],
        loadComponent: () =>
          import('./features/finance/payment-list/payment-list.component').then((m) => m.PaymentListComponent),
      },
      {
        path: 'documents',
        loadComponent: () =>
          import('./features/documents/document-list/document-list.component').then((m) => m.DocumentListComponent),
      },
      {
        path: 'documents/new',
        canActivate: [roleGuard(['STUDY_MANAGER', 'SITE_COORDINATOR', 'ADMIN'])],
        loadComponent: () =>
          import('./features/documents/document-upload/document-upload.component').then(
            (m) => m.DocumentUploadComponent,
          ),
      },
      {
        path: 'documents/approval-queue',
        canActivate: [roleGuard(['STUDY_MANAGER', 'QA_COMPLIANCE_AUDITOR', 'ADMIN'])],
        loadComponent: () =>
          import('./features/documents/document-approval-queue/document-approval-queue.component').then(
            (m) => m.DocumentApprovalQueueComponent,
          ),
      },
      {
        path: 'documents/:id',
        loadComponent: () =>
          import('./features/documents/document-detail/document-detail.component').then(
            (m) => m.DocumentDetailComponent,
          ),
      },
      {
        path: 'admin/audit-log',
        canActivate: [roleGuard(['ADMIN', 'QA_COMPLIANCE_AUDITOR'])],
        loadComponent: () =>
          import('./features/admin/audit-log/audit-log.component').then((m) => m.AuditLogComponent),
      },
      {
        path: 'tasks',
        canActivate: [
          roleGuard([
            'STUDY_MANAGER', 'ADMIN', 'SITE_COORDINATOR', 'INVESTIGATOR', 'CRA_MONITOR',
            'DATA_MANAGEMENT', 'FINANCE_MANAGER', 'QA_COMPLIANCE_AUDITOR', 'CLINICAL_LEADERSHIP',
            'EXECUTIVE', 'SPONSOR_CRO_LEADERSHIP',
          ]),
        ],
        loadComponent: () => import('./features/tasks/task-inbox/task-inbox.component').then((m) => m.TaskInboxComponent),
      },
      {
        path: 'adverse-events/board',
        canActivate: [roleGuard(['INVESTIGATOR', 'STUDY_MANAGER', 'ADMIN', 'CRA_MONITOR'])],
        loadComponent: () =>
          import('./features/adverse-events/ae-board/ae-board.component').then((m) => m.AeBoardComponent),
      },
      {
        path: 'notifications',
        loadComponent: () =>
          import('./features/notifications/notification-list/notification-list.component').then(
            (m) => m.NotificationListComponent,
          ),
      },
      { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
    ],
  },
  {
    path: 'patient',
    component: PatientShellComponent,
    canActivate: [authGuard, roleGuard(['PATIENT_SUBJECT'])],
    children: [
      {
        path: 'visits',
        loadComponent: () =>
          import('./features/patient/patient-visits/patient-visits.component').then((m) => m.PatientVisitsComponent),
      },
      {
        path: 'documents',
        loadComponent: () =>
          import('./features/patient/patient-documents/patient-documents.component').then(
            (m) => m.PatientDocumentsComponent,
          ),
      },
      {
        path: 'profile',
        loadComponent: () =>
          import('./features/patient/patient-profile/patient-profile.component').then((m) => m.PatientProfileComponent),
      },
      {
        path: 'report-health-issue',
        loadComponent: () =>
          import('./features/patient/patient-adverse-event-report/patient-adverse-event-report.component').then(
            (m) => m.PatientAdverseEventReportComponent,
          ),
      },
      {
        path: 'notifications',
        loadComponent: () =>
          import('./features/notifications/notification-list/notification-list.component').then(
            (m) => m.NotificationListComponent,
          ),
      },
      { path: '', pathMatch: 'full', redirectTo: 'visits' },
    ],
  },
  { path: '**', redirectTo: 'dashboard' },
];
