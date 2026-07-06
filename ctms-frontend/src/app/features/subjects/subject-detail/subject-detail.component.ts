import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, OnInit, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { ActivatedRoute } from '@angular/router';
import { HasRoleDirective } from '../../../core/auth/has-role.directive';
import { AdverseEventResponse, AdverseEventService } from '../../../core/adverse-events/adverse-event.service';
import { DocumentResponse, DocumentService } from '../../../core/documents/document.service';
import { toIsoDate } from '../../../core/utils/date-utils';
import { StatusChipPipe } from '../../../core/utils/status-chip.pipe';
import { DOCUMENT_CATEGORIES } from '../../documents/document-upload/document-upload.component';
import {
  ProtocolDeviationResponse,
  ProtocolDeviationService,
} from '../../../core/protocol-deviations/protocol-deviation.service';
import {
  PortalAccountResponse,
  SubjectResponse,
  SubjectService,
  SubjectStatusHistoryResponse,
} from '../../../core/subjects/subject.service';
import {
  TestResultAttachmentResponse,
  TestResultResponse,
  TestResultService,
} from '../../../core/test-results/test-result.service';
import { SubjectVisitScheduleResponse, VisitService } from '../../../core/visits/visit.service';

type VisitActionType = 'complete' | 'miss' | 'reschedule';

const NEXT_STATUS: Record<string, string> = {
  SCREENED: 'ENROLLED',
  ENROLLED: 'IN_TREATMENT',
  IN_TREATMENT: 'COMPLETED',
};

const STATUS_LABELS: Record<string, string> = {
  SCREENED: 'Screened',
  ENROLLED: 'Enrolled',
  IN_TREATMENT: 'In Treatment',
  COMPLETED: 'Completed',
  WITHDRAWN: 'Withdrawn',
};

@Component({
  selector: 'app-subject-detail',
  standalone: true,
  imports: [
    MatButtonModule,
    MatCheckboxModule,
    MatDatepickerModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    ReactiveFormsModule,
    DatePipe,
    DecimalPipe,
    HasRoleDirective,
    StatusChipPipe,
  ],
  templateUrl: './subject-detail.component.html',
})
export class SubjectDetailComponent implements OnInit {
  readonly subject = signal<SubjectResponse | null>(null);
  readonly history = signal<SubjectStatusHistoryResponse[]>([]);
  readonly errorMessage = signal<string | null>(null);
  readonly editing = signal(false);
  readonly showWithdrawForm = signal(false);

  readonly visitSchedule = signal<SubjectVisitScheduleResponse | null>(null);
  readonly visitErrorMessage = signal<string | null>(null);
  readonly openVisitAction = signal<{ visitId: number; type: VisitActionType } | null>(null);
  readonly showAdHocForm = signal(false);

  readonly testResults = signal<TestResultResponse[]>([]);
  readonly testResultErrorMessage = signal<string | null>(null);
  readonly showTestResultForm = signal(false);
  readonly attachmentsByResultId = signal<Record<number, TestResultAttachmentResponse[]>>({});

  readonly adverseEvents = signal<AdverseEventResponse[]>([]);
  readonly adverseEventErrorMessage = signal<string | null>(null);
  readonly showAdverseEventForm = signal(false);
  readonly openAeAction = signal<{ id: number; type: 'transition' | 'resolve' } | null>(null);

  readonly protocolDeviations = signal<ProtocolDeviationResponse[]>([]);
  readonly protocolDeviationErrorMessage = signal<string | null>(null);
  readonly showProtocolDeviationForm = signal(false);

  readonly documents = signal<DocumentResponse[]>([]);
  readonly documentErrorMessage = signal<string | null>(null);
  readonly showDocumentForm = signal(false);
  readonly documentCategories = DOCUMENT_CATEGORIES;
  readonly selectedDocumentFile = signal<File | null>(null);

  readonly portalAccountErrorMessage = signal<string | null>(null);
  readonly portalAccountResult = signal<PortalAccountResponse | null>(null);

  readonly justificationControl = new FormControl('', { nonNullable: true, validators: Validators.required });
  readonly reasonCodeControl = new FormControl('', { nonNullable: true, validators: Validators.required });
  readonly withdrawPasswordControl = new FormControl('', { nonNullable: true, validators: Validators.required });

  readonly completeVisitForm = new FormGroup({
    actualDate: new FormControl<Date | null>(null, { validators: Validators.required }),
    actualTime: new FormControl<string | null>(null),
    notes: new FormControl<string | null>(null),
  });

  readonly missVisitForm = new FormGroup({
    reasonCode: new FormControl('', { nonNullable: true, validators: Validators.required }),
  });

  readonly rescheduleVisitForm = new FormGroup({
    newDate: new FormControl<Date | null>(null, { validators: Validators.required }),
    reasonCode: new FormControl('', { nonNullable: true, validators: Validators.required }),
  });

  readonly adHocVisitForm = new FormGroup({
    name: new FormControl('', { nonNullable: true, validators: Validators.required }),
    scheduledDate: new FormControl<Date | null>(null, { validators: Validators.required }),
    visitType: new FormControl<'ONSITE' | 'REMOTE'>('ONSITE', { nonNullable: true }),
    requiredProcedures: new FormControl<string | null>(null),
    reasonCode: new FormControl('', { nonNullable: true, validators: Validators.required }),
  });

  readonly testResultForm = new FormGroup({
    visitId: new FormControl<number | null>(null, { validators: Validators.required }),
    testName: new FormControl('', { nonNullable: true, validators: Validators.required }),
    resultValue: new FormControl('', { nonNullable: true, validators: Validators.required }),
    units: new FormControl<string | null>(null),
    referenceRange: new FormControl<string | null>(null),
    abnormal: new FormControl(false, { nonNullable: true }),
    notes: new FormControl<string | null>(null),
  });

  readonly adverseEventForm = new FormGroup({
    visitId: new FormControl<number | null>(null),
    description: new FormControl('', { nonNullable: true, validators: Validators.required }),
    severity: new FormControl<'MILD' | 'MODERATE' | 'SEVERE' | 'LIFE_THREATENING'>('MILD', { nonNullable: true }),
  });

  readonly aeJustificationControl = new FormControl('', { nonNullable: true, validators: Validators.required });
  readonly aeResolutionNotesControl = new FormControl('', { nonNullable: true, validators: Validators.required });
  readonly aeResolvePasswordControl = new FormControl('', { nonNullable: true, validators: Validators.required });

  readonly protocolDeviationForm = new FormGroup({
    description: new FormControl('', { nonNullable: true, validators: Validators.required }),
    severity: new FormControl<'MINOR' | 'MAJOR' | 'CRITICAL'>('MINOR', { nonNullable: true }),
    deviationDate: new FormControl<Date | null>(null, { validators: Validators.required }),
  });

  readonly documentForm = new FormGroup({
    title: new FormControl('', { nonNullable: true, validators: Validators.required }),
    category: new FormControl('', { nonNullable: true, validators: Validators.required }),
  });

  readonly editForm = new FormGroup({
    firstName: new FormControl('', { nonNullable: true, validators: Validators.required }),
    lastName: new FormControl('', { nonNullable: true, validators: Validators.required }),
    gender: new FormControl(''),
    contactPhone: new FormControl(''),
    contactEmail: new FormControl('', { validators: Validators.email }),
    address: new FormControl(''),
    emergencyContact: new FormControl(''),
    notes: new FormControl(''),
    medicalHistory: new FormControl(''),
  });

  private subjectId!: number;

  constructor(
    private readonly route: ActivatedRoute,
    private readonly subjectService: SubjectService,
    private readonly visitService: VisitService,
    private readonly testResultService: TestResultService,
    private readonly adverseEventService: AdverseEventService,
    private readonly protocolDeviationService: ProtocolDeviationService,
    private readonly documentService: DocumentService,
  ) {}

  ngOnInit(): void {
    this.subjectId = Number(this.route.snapshot.paramMap.get('id'));
    this.load();
  }

  load(): void {
    this.subjectService.get(this.subjectId).subscribe((s) => this.subject.set(s));
    this.subjectService.history(this.subjectId).subscribe((h) => this.history.set(h));
    this.loadVisits();
    this.loadTestResults();
    this.loadAdverseEvents();
    this.loadProtocolDeviations();
    this.loadDocuments();
  }

  loadTestResults(): void {
    this.testResultService.list(this.subjectId).subscribe((results) => {
      this.testResults.set(results);
      results.forEach((r) => this.loadAttachments(r.id));
    });
  }

  loadAttachments(testResultId: number): void {
    this.testResultService.attachments(testResultId).subscribe((attachments) => {
      this.attachmentsByResultId.update((map) => ({ ...map, [testResultId]: attachments }));
    });
  }

  attachmentsFor(testResultId: number): TestResultAttachmentResponse[] {
    return this.attachmentsByResultId()[testResultId] ?? [];
  }

  downloadAttachment(attachment: TestResultAttachmentResponse): void {
    this.testResultService.download(attachment.id).subscribe((blob) => {
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement('a');
      anchor.href = url;
      anchor.download = attachment.fileName;
      anchor.click();
      URL.revokeObjectURL(url);
    });
  }

  openTestResultForm(): void {
    this.testResultErrorMessage.set(null);
    const firstVisitId = this.visitSchedule()?.visits[0]?.id ?? null;
    this.testResultForm.reset({
      visitId: firstVisitId,
      testName: '',
      resultValue: '',
      units: null,
      referenceRange: null,
      abnormal: false,
      notes: null,
    });
    this.showTestResultForm.set(true);
  }

  cancelTestResultForm(): void {
    this.showTestResultForm.set(false);
  }

  submitTestResult(): void {
    if (this.testResultForm.invalid) {
      return;
    }
    const raw = this.testResultForm.getRawValue();
    this.testResultErrorMessage.set(null);
    this.testResultService
      .record({
        subjectId: this.subjectId,
        visitId: raw.visitId!,
        testName: raw.testName,
        resultValue: raw.resultValue,
        units: raw.units || null,
        referenceRange: raw.referenceRange || null,
        abnormal: raw.abnormal,
        notes: raw.notes || null,
      })
      .subscribe({
        next: () => {
          this.showTestResultForm.set(false);
          this.loadTestResults();
        },
        error: (err) => this.testResultErrorMessage.set(err.error?.message ?? 'Could not record test result.'),
      });
  }

  reviewTestResult(id: number): void {
    this.testResultErrorMessage.set(null);
    this.testResultService.review(id).subscribe({
      next: () => this.loadTestResults(),
      error: (err) => this.testResultErrorMessage.set(err.error?.message ?? 'Could not review test result.'),
    });
  }

  uploadAttachment(testResultId: number, input: HTMLInputElement): void {
    const file = input.files?.[0];
    if (!file) {
      return;
    }
    this.testResultErrorMessage.set(null);
    this.testResultService.upload(testResultId, file).subscribe({
      next: () => {
        input.value = '';
        this.loadAttachments(testResultId);
      },
      error: (err) => this.testResultErrorMessage.set(err.error?.message ?? 'Could not upload attachment.'),
    });
  }

  loadAdverseEvents(): void {
    this.adverseEventService.list(this.subjectId).subscribe((events) => this.adverseEvents.set(events));
  }

  openAdverseEventForm(): void {
    this.adverseEventErrorMessage.set(null);
    this.adverseEventForm.reset({ visitId: null, description: '', severity: 'MILD' });
    this.showAdverseEventForm.set(true);
  }

  cancelAdverseEventForm(): void {
    this.showAdverseEventForm.set(false);
  }

  submitAdverseEvent(): void {
    if (this.adverseEventForm.invalid) {
      return;
    }
    const raw = this.adverseEventForm.getRawValue();
    this.adverseEventErrorMessage.set(null);
    this.adverseEventService
      .report({ subjectId: this.subjectId, visitId: raw.visitId, description: raw.description, severity: raw.severity })
      .subscribe({
        next: () => {
          this.showAdverseEventForm.set(false);
          this.loadAdverseEvents();
        },
        error: (err) => this.adverseEventErrorMessage.set(err.error?.message ?? 'Could not report adverse event.'),
      });
  }

  openAeTransition(id: number): void {
    this.adverseEventErrorMessage.set(null);
    this.aeJustificationControl.reset('');
    this.openAeAction.set({ id, type: 'transition' });
  }

  openAeResolve(id: number): void {
    this.adverseEventErrorMessage.set(null);
    this.aeResolutionNotesControl.reset('');
    this.aeResolvePasswordControl.reset('');
    this.openAeAction.set({ id, type: 'resolve' });
  }

  cancelAeAction(): void {
    this.openAeAction.set(null);
  }

  submitAeTransition(id: number): void {
    if (this.aeJustificationControl.invalid) {
      return;
    }
    this.adverseEventErrorMessage.set(null);
    this.adverseEventService.transition(id, 'UNDER_REVIEW', this.aeJustificationControl.value).subscribe({
      next: () => {
        this.openAeAction.set(null);
        this.loadAdverseEvents();
      },
      error: (err) => this.adverseEventErrorMessage.set(err.error?.message ?? 'Could not transition adverse event.'),
    });
  }

  submitAeResolve(id: number): void {
    if (this.aeResolutionNotesControl.invalid || this.aeResolvePasswordControl.invalid) {
      return;
    }
    this.adverseEventErrorMessage.set(null);
    this.adverseEventService.resolve(id, this.aeResolutionNotesControl.value, this.aeResolvePasswordControl.value).subscribe({
      next: () => {
        this.openAeAction.set(null);
        this.loadAdverseEvents();
      },
      error: (err) => {
        const message =
          err.status === 401 ? 'Incorrect password. Please try again.' : (err.error?.message ?? 'Could not resolve adverse event.');
        this.adverseEventErrorMessage.set(message);
      },
    });
  }

  loadProtocolDeviations(): void {
    this.protocolDeviationService.list(this.subjectId).subscribe((deviations) => this.protocolDeviations.set(deviations));
  }

  openProtocolDeviationForm(): void {
    this.protocolDeviationErrorMessage.set(null);
    this.protocolDeviationForm.reset({ description: '', severity: 'MINOR', deviationDate: new Date() });
    this.showProtocolDeviationForm.set(true);
  }

  cancelProtocolDeviationForm(): void {
    this.showProtocolDeviationForm.set(false);
  }

  submitProtocolDeviation(): void {
    if (this.protocolDeviationForm.invalid) {
      return;
    }
    const raw = this.protocolDeviationForm.getRawValue();
    this.protocolDeviationErrorMessage.set(null);
    this.protocolDeviationService
      .report({
        subjectId: this.subjectId,
        description: raw.description,
        severity: raw.severity,
        deviationDate: toIsoDate(raw.deviationDate)!,
      })
      .subscribe({
        next: () => {
          this.showProtocolDeviationForm.set(false);
          this.loadProtocolDeviations();
        },
        error: (err) => this.protocolDeviationErrorMessage.set(err.error?.message ?? 'Could not report protocol deviation.'),
      });
  }

  loadDocuments(): void {
    this.documentService.listBySubject(this.subjectId).subscribe((docs) => this.documents.set(docs));
  }

  openDocumentForm(): void {
    this.documentErrorMessage.set(null);
    this.documentForm.reset({ title: '', category: '' });
    this.selectedDocumentFile.set(null);
    this.showDocumentForm.set(true);
  }

  cancelDocumentForm(): void {
    this.showDocumentForm.set(false);
  }

  onDocumentFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.selectedDocumentFile.set(input.files?.[0] ?? null);
  }

  submitDocument(): void {
    if (this.documentForm.invalid || !this.selectedDocumentFile()) {
      return;
    }
    const { title, category } = this.documentForm.getRawValue();
    const study = this.subject();
    this.documentErrorMessage.set(null);
    this.documentService
      .create(title, category, study?.studyId ?? null, this.subjectId, this.selectedDocumentFile()!)
      .subscribe({
        next: () => {
          this.showDocumentForm.set(false);
          this.loadDocuments();
        },
        error: (err) => this.documentErrorMessage.set(err.error?.message ?? 'Could not upload document.'),
      });
  }

  loadVisits(): void {
    this.visitService.schedule(this.subjectId).subscribe((schedule) => this.visitSchedule.set(schedule));
  }

  isVisitHighlighted(status: string): boolean {
    return status === 'MISSED' || status === 'RESCHEDULED';
  }

  openComplete(visitId: number): void {
    this.visitErrorMessage.set(null);
    this.completeVisitForm.reset({ actualDate: new Date(), actualTime: null, notes: null });
    this.openVisitAction.set({ visitId, type: 'complete' });
  }

  openMiss(visitId: number): void {
    this.visitErrorMessage.set(null);
    this.missVisitForm.reset({ reasonCode: '' });
    this.openVisitAction.set({ visitId, type: 'miss' });
  }

  openReschedule(visitId: number): void {
    this.visitErrorMessage.set(null);
    this.rescheduleVisitForm.reset({ newDate: null, reasonCode: '' });
    this.openVisitAction.set({ visitId, type: 'reschedule' });
  }

  cancelVisitAction(): void {
    this.openVisitAction.set(null);
  }

  submitComplete(visitId: number): void {
    if (this.completeVisitForm.invalid) {
      return;
    }
    const raw = this.completeVisitForm.getRawValue();
    this.visitErrorMessage.set(null);
    this.visitService
      .complete(visitId, { actualDate: toIsoDate(raw.actualDate)!, actualTime: raw.actualTime || null, notes: raw.notes || null })
      .subscribe({
        next: () => {
          this.openVisitAction.set(null);
          this.loadVisits();
        },
        error: (err) => this.visitErrorMessage.set(err.error?.message ?? 'Could not mark visit completed.'),
      });
  }

  submitMiss(visitId: number): void {
    if (this.missVisitForm.invalid) {
      return;
    }
    this.visitErrorMessage.set(null);
    this.visitService.miss(visitId, { reasonCode: this.missVisitForm.getRawValue().reasonCode }).subscribe({
      next: () => {
        this.openVisitAction.set(null);
        this.loadVisits();
      },
      error: (err) => this.visitErrorMessage.set(err.error?.message ?? 'Could not mark visit missed.'),
    });
  }

  submitReschedule(visitId: number): void {
    if (this.rescheduleVisitForm.invalid) {
      return;
    }
    const raw = this.rescheduleVisitForm.getRawValue();
    this.visitErrorMessage.set(null);
    this.visitService.reschedule(visitId, { newDate: toIsoDate(raw.newDate)!, reasonCode: raw.reasonCode }).subscribe({
      next: () => {
        this.openVisitAction.set(null);
        this.loadVisits();
      },
      error: (err) => this.visitErrorMessage.set(err.error?.message ?? 'Could not reschedule visit.'),
    });
  }

  openAdHocForm(): void {
    this.visitErrorMessage.set(null);
    this.adHocVisitForm.reset({
      name: '',
      scheduledDate: null,
      visitType: 'ONSITE',
      requiredProcedures: null,
      reasonCode: '',
    });
    this.showAdHocForm.set(true);
  }

  cancelAdHocForm(): void {
    this.showAdHocForm.set(false);
  }

  submitAdHoc(): void {
    if (this.adHocVisitForm.invalid) {
      return;
    }
    const raw = this.adHocVisitForm.getRawValue();
    this.visitErrorMessage.set(null);
    this.visitService
      .scheduleAdHoc(this.subjectId, {
        name: raw.name,
        scheduledDate: toIsoDate(raw.scheduledDate)!,
        visitType: raw.visitType,
        requiredProcedures: raw.requiredProcedures || null,
        reasonCode: raw.reasonCode,
      })
      .subscribe({
        next: () => {
          this.showAdHocForm.set(false);
          this.loadVisits();
        },
        error: (err) => this.visitErrorMessage.set(err.error?.message ?? 'Could not schedule ad-hoc visit.'),
      });
  }

  statusLabel(status: string): string {
    return STATUS_LABELS[status] ?? status;
  }

  nextStatus(status: string): string | null {
    return NEXT_STATUS[status] ?? null;
  }

  isWithdrawable(status: string): boolean {
    return status === 'SCREENED' || status === 'ENROLLED' || status === 'IN_TREATMENT';
  }

  advance(): void {
    const s = this.subject();
    const next = s ? this.nextStatus(s.status) : null;
    if (!s || !next || this.justificationControl.invalid) {
      return;
    }
    this.errorMessage.set(null);
    this.subjectService.transition(this.subjectId, next, this.justificationControl.value).subscribe({
      next: () => {
        this.justificationControl.setValue('');
        this.load();
      },
      error: (err) => this.errorMessage.set(err.error?.message ?? 'Transition failed.'),
    });
  }

  confirmWithdraw(): void {
    if (this.reasonCodeControl.invalid || this.withdrawPasswordControl.invalid) {
      return;
    }
    this.errorMessage.set(null);
    this.subjectService.withdraw(this.subjectId, this.reasonCodeControl.value, this.withdrawPasswordControl.value).subscribe({
      next: () => {
        this.reasonCodeControl.setValue('');
        this.withdrawPasswordControl.setValue('');
        this.showWithdrawForm.set(false);
        this.load();
      },
      error: (err) => {
        const message = err.status === 401 ? 'Incorrect password. Please try again.' : (err.error?.message ?? 'Withdrawal failed.');
        this.errorMessage.set(message);
      },
    });
  }

  createPortalAccount(): void {
    this.portalAccountErrorMessage.set(null);
    this.subjectService.createPortalAccount(this.subjectId).subscribe({
      next: (result) => this.portalAccountResult.set(result),
      error: (err) => this.portalAccountErrorMessage.set(err.error?.message ?? 'Could not create portal account.'),
    });
  }

  resetPortalPassword(): void {
    this.portalAccountErrorMessage.set(null);
    this.subjectService.resetPortalPassword(this.subjectId).subscribe({
      next: (result) => this.portalAccountResult.set(result),
      error: (err) => this.portalAccountErrorMessage.set(err.error?.message ?? 'Could not reset portal password.'),
    });
  }

  dismissPortalAccountResult(): void {
    this.portalAccountResult.set(null);
  }

  startEdit(): void {
    const s = this.subject();
    if (!s) {
      return;
    }
    this.editForm.setValue({
      firstName: s.firstName,
      lastName: s.lastName,
      gender: s.gender ?? '',
      contactPhone: s.contactPhone ?? '',
      contactEmail: s.contactEmail ?? '',
      address: s.address ?? '',
      emergencyContact: s.emergencyContact ?? '',
      notes: s.notes ?? '',
      medicalHistory: s.medicalHistory ?? '',
    });
    this.editing.set(true);
  }

  cancelEdit(): void {
    this.editing.set(false);
  }

  saveEdit(): void {
    if (this.editForm.invalid) {
      return;
    }
    const raw = this.editForm.getRawValue();
    this.errorMessage.set(null);
    this.subjectService
      .update(this.subjectId, {
        firstName: raw.firstName,
        lastName: raw.lastName,
        gender: raw.gender || null,
        contactPhone: raw.contactPhone || null,
        contactEmail: raw.contactEmail || null,
        address: raw.address || null,
        emergencyContact: raw.emergencyContact || null,
        notes: raw.notes || null,
        medicalHistory: raw.medicalHistory || null,
      })
      .subscribe({
        next: () => {
          this.editing.set(false);
          this.load();
        },
        error: (err) => this.errorMessage.set(err.error?.message ?? 'Could not update subject.'),
      });
  }
}
