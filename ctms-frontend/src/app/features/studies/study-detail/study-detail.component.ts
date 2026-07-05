import { DatePipe } from '@angular/common';
import { Component, OnInit, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { ActivatedRoute } from '@angular/router';
import { HasRoleDirective } from '../../../core/auth/has-role.directive';
import {
  StudyResponse,
  StudyService,
  StudyStatusHistoryResponse,
} from '../../../core/studies/study.service';
import { StudyCloseoutDialogComponent } from '../study-closeout-dialog/study-closeout-dialog.component';
import { StudyTransitionDialogComponent } from '../study-transition-dialog/study-transition-dialog.component';

const NEXT_STATUS: Record<string, string> = {
  DRAFT: 'ACTIVE',
  ACTIVE: 'CONDUCT',
  CONDUCT: 'CLOSEOUT',
};

const ALL_STATUSES = ['DRAFT', 'ACTIVE', 'CONDUCT', 'CLOSEOUT'];

@Component({
  selector: 'app-study-detail',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    DatePipe,
    HasRoleDirective,
  ],
  templateUrl: './study-detail.component.html',
})
export class StudyDetailComponent implements OnInit {
  readonly allStatuses = ALL_STATUSES;
  readonly study = signal<StudyResponse | null>(null);
  readonly history = signal<StudyStatusHistoryResponse[]>([]);
  readonly editMode = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly transitionErrorMessage = signal<string | null>(null);

  form = new FormGroup({
    name: new FormControl('', { nonNullable: true, validators: Validators.required }),
    protocolId: new FormControl('', { nonNullable: true, validators: Validators.required }),
    protocolVersion: new FormControl('', { nonNullable: true, validators: Validators.required }),
    phase: new FormControl('', { nonNullable: true, validators: Validators.required }),
    sponsor: new FormControl('', { nonNullable: true, validators: Validators.required }),
    plannedStartDate: new FormControl<string | null>(null),
    plannedEndDate: new FormControl<string | null>(null),
    actualStartDate: new FormControl<string | null>(null),
    actualEndDate: new FormControl<string | null>(null),
    description: new FormControl<string | null>(null),
  });

  private studyId!: number;

  constructor(
    private readonly route: ActivatedRoute,
    private readonly studyService: StudyService,
    private readonly dialog: MatDialog,
  ) {}

  ngOnInit(): void {
    this.studyId = Number(this.route.snapshot.paramMap.get('id'));
    this.load();
  }

  load(): void {
    this.studyService.get(this.studyId).subscribe((study) => {
      this.study.set(study);
      this.populateForm(study);
    });
    this.studyService.history(this.studyId).subscribe((h) => this.history.set(h));
  }

  private populateForm(study: StudyResponse): void {
    this.form.setValue({
      name: study.name,
      protocolId: study.protocolId,
      protocolVersion: study.protocolVersion,
      phase: study.phase,
      sponsor: study.sponsor,
      plannedStartDate: study.plannedStartDate,
      plannedEndDate: study.plannedEndDate,
      actualStartDate: study.actualStartDate,
      actualEndDate: study.actualEndDate,
      description: study.description,
    });
    if (study.status !== 'DRAFT') {
      this.form.controls.protocolId.disable();
    } else {
      this.form.controls.protocolId.enable();
    }
  }

  isFullyLocked(): boolean {
    return this.study()?.status === 'CLOSEOUT';
  }

  toggleEdit(): void {
    this.editMode.update((v) => !v);
    this.errorMessage.set(null);
  }

  saveEdit(): void {
    if (this.form.invalid) {
      return;
    }
    this.errorMessage.set(null);
    this.studyService.update(this.studyId, this.form.getRawValue()).subscribe({
      next: (study) => {
        this.study.set(study);
        this.populateForm(study);
        this.editMode.set(false);
      },
      error: (err) => this.errorMessage.set(err.error?.message ?? 'Could not update study.'),
    });
  }

  nextStatus(): string | null {
    const status = this.study()?.status;
    if (!status) {
      return null;
    }
    return NEXT_STATUS[status] ?? null;
  }

  canAdvance(): boolean {
    const status = this.study()?.status;
    return status === 'DRAFT' || status === 'ACTIVE';
  }

  canCloseOut(): boolean {
    return this.study()?.status === 'CONDUCT';
  }

  advance(): void {
    const target = this.nextStatus();
    if (!target) {
      return;
    }
    const dialogRef = this.dialog.open(StudyTransitionDialogComponent, {
      data: { targetStatus: target },
    });
    dialogRef.afterClosed().subscribe((justification: string | undefined) => {
      if (!justification) {
        return;
      }
      this.transitionErrorMessage.set(null);
      this.studyService.transition(this.studyId, target, justification).subscribe({
        next: () => this.load(),
        error: (err) => this.transitionErrorMessage.set(err.error?.message ?? 'Transition failed.'),
      });
    });
  }

  closeOut(): void {
    const dialogRef = this.dialog.open(StudyCloseoutDialogComponent);
    dialogRef.afterClosed().subscribe((result) => {
      if (!result) {
        return;
      }
      this.transitionErrorMessage.set(null);
      this.studyService.closeout(this.studyId, result.password, result.reason).subscribe({
        next: () => this.load(),
        error: (err) => {
          // The backend reuses its generic login-failure exception for e-signature re-auth too,
          // so its message ("Invalid username or password") doesn't fit this already-logged-in context.
          const message = err.status === 401 ? 'Incorrect password. Please try again.' : (err.error?.message ?? 'Closeout failed.');
          this.transitionErrorMessage.set(message);
        },
      });
    });
  }
}
