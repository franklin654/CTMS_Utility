import { Component, OnInit, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { HasRoleDirective } from '../../../core/auth/has-role.directive';
import { SubjectResponse, SubjectService } from '../../../core/subjects/subject.service';

const STATUS_LABELS: Record<string, string> = {
  SCREENED: 'Screened',
  ENROLLED: 'Enrolled',
  IN_TREATMENT: 'In Treatment',
  COMPLETED: 'Completed',
  WITHDRAWN: 'Withdrawn',
};

@Component({
  selector: 'app-subject-list',
  standalone: true,
  imports: [MatButtonModule, MatPaginatorModule, RouterLink, HasRoleDirective],
  templateUrl: './subject-list.component.html',
})
export class SubjectListComponent implements OnInit {
  readonly subjects = signal<SubjectResponse[]>([]);
  readonly totalElements = signal(0);
  readonly pageSize = signal(20);
  readonly pageIndex = signal(0);
  studyId: number | null = null;

  constructor(
    private readonly subjectService: SubjectService,
    private readonly route: ActivatedRoute,
  ) {}

  ngOnInit(): void {
    const studyIdParam = this.route.snapshot.queryParamMap.get('studyId');
    this.studyId = studyIdParam ? Number(studyIdParam) : null;
    this.load();
  }

  load(): void {
    this.subjectService
      .list(this.studyId ?? undefined, undefined, undefined, this.pageIndex(), this.pageSize())
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
