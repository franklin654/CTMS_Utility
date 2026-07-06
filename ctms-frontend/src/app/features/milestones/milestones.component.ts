import { DatePipe } from '@angular/common';
import { Component, OnInit, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { ActivatedRoute } from '@angular/router';
import { HasRoleDirective } from '../../core/auth/has-role.directive';
import { MilestoneResponse, MilestoneService } from '../../core/milestones/milestone.service';
import { StudyResponse, StudyService } from '../../core/studies/study.service';
import { toIsoDate } from '../../core/utils/date-utils';

@Component({
  selector: 'app-milestones',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatDatepickerModule,
    DatePipe,
    HasRoleDirective,
  ],
  templateUrl: './milestones.component.html',
})
export class MilestonesComponent implements OnInit {
  readonly study = signal<StudyResponse | null>(null);
  readonly milestones = signal<MilestoneResponse[]>([]);
  readonly errorMessage = signal<string | null>(null);
  readonly showCreateForm = signal(false);
  readonly recordingActualForId = signal<number | null>(null);

  private studyId!: number;

  readonly createForm = new FormGroup({
    milestoneType: new FormControl<'FPI' | 'LPI' | 'LPO' | 'DBL'>('FPI', { nonNullable: true }),
    plannedDate: new FormControl<Date | null>(null, { validators: Validators.required }),
  });

  readonly actualDateControl = new FormControl<Date | null>(null, { validators: Validators.required });

  constructor(
    private readonly route: ActivatedRoute,
    private readonly studyService: StudyService,
    private readonly milestoneService: MilestoneService,
  ) {}

  ngOnInit(): void {
    this.studyId = Number(this.route.snapshot.paramMap.get('studyId'));
    this.studyService.get(this.studyId).subscribe((s) => this.study.set(s));
    this.load();
  }

  load(): void {
    this.milestoneService.listByStudy(this.studyId).subscribe((milestones) => this.milestones.set(milestones));
  }

  openCreateForm(): void {
    this.errorMessage.set(null);
    this.createForm.reset({ milestoneType: 'FPI', plannedDate: null });
    this.showCreateForm.set(true);
  }

  cancelCreateForm(): void {
    this.showCreateForm.set(false);
  }

  submitCreate(): void {
    if (this.createForm.invalid) {
      return;
    }
    const { milestoneType, plannedDate } = this.createForm.getRawValue();
    this.errorMessage.set(null);
    this.milestoneService.create({ studyId: this.studyId, milestoneType, plannedDate: toIsoDate(plannedDate)! }).subscribe({
      next: () => {
        this.showCreateForm.set(false);
        this.load();
      },
      error: (err) => this.errorMessage.set(err.error?.message ?? 'Could not create milestone.'),
    });
  }

  openRecordActual(id: number): void {
    this.errorMessage.set(null);
    this.actualDateControl.reset(new Date());
    this.recordingActualForId.set(id);
  }

  cancelRecordActual(): void {
    this.recordingActualForId.set(null);
  }

  submitRecordActual(id: number): void {
    if (this.actualDateControl.invalid) {
      return;
    }
    this.errorMessage.set(null);
    this.milestoneService.recordActual(id, toIsoDate(this.actualDateControl.value)!).subscribe({
      next: () => {
        this.recordingActualForId.set(null);
        this.load();
      },
      error: (err) => this.errorMessage.set(err.error?.message ?? 'Could not record actual date.'),
    });
  }
}
