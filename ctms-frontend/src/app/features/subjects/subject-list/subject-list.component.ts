import { Component, OnInit, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatSelectModule } from '@angular/material/select';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { debounceTime, distinctUntilChanged } from 'rxjs';
import { HasRoleDirective } from '../../../core/auth/has-role.directive';
import { SubjectResponse, SubjectService } from '../../../core/subjects/subject.service';

const STATUS_LABELS: Record<string, string> = {
  SCREENED: 'Screened',
  ENROLLED: 'Enrolled',
  IN_TREATMENT: 'In Treatment',
  COMPLETED: 'Completed',
  WITHDRAWN: 'Withdrawn',
};

const ALL_STATUSES = Object.keys(STATUS_LABELS);

@Component({
  selector: 'app-subject-list',
  standalone: true,
  imports: [
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatPaginatorModule,
    ReactiveFormsModule,
    RouterLink,
    HasRoleDirective,
  ],
  templateUrl: './subject-list.component.html',
})
export class SubjectListComponent implements OnInit {
  readonly statuses = ALL_STATUSES;
  readonly subjects = signal<SubjectResponse[]>([]);
  readonly totalElements = signal(0);
  readonly pageSize = signal(20);
  readonly pageIndex = signal(0);
  studyId: number | null = null;

  readonly filterForm = new FormGroup({
    search: new FormControl('', { nonNullable: true }),
    status: new FormControl<string | null>(null),
  });

  constructor(
    private readonly subjectService: SubjectService,
    private readonly route: ActivatedRoute,
  ) {}

  ngOnInit(): void {
    const studyIdParam = this.route.snapshot.queryParamMap.get('studyId');
    this.studyId = studyIdParam ? Number(studyIdParam) : null;
    this.load();

    this.filterForm.valueChanges.pipe(debounceTime(300), distinctUntilChanged((a, b) => a.search === b.search && a.status === b.status)).subscribe(() => {
      this.pageIndex.set(0);
      this.load();
    });
  }

  load(): void {
    const { search, status } = this.filterForm.getRawValue();
    this.subjectService
      .list(this.studyId ?? undefined, undefined, search || undefined, this.pageIndex(), this.pageSize(), status)
      .subscribe((page) => {
        this.subjects.set(page.content);
        this.totalElements.set(page.totalElements);
      });
  }

  onPage(event: PageEvent): void {
    this.pageIndex.set(event.pageIndex);
    this.pageSize.set(event.pageSize);
    this.load();
  }

  statusLabel(status: string): string {
    return STATUS_LABELS[status] ?? status;
  }
}
