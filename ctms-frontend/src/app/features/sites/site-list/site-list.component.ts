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
import { SiteResponse, SiteService } from '../../../core/sites/site.service';

const STATUSES = ['PENDING_ACTIVATION', 'ACTIVE'];

@Component({
  selector: 'app-site-list',
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
  templateUrl: './site-list.component.html',
})
export class SiteListComponent implements OnInit {
  readonly statuses = STATUSES;
  readonly sites = signal<SiteResponse[]>([]);
  readonly totalElements = signal(0);
  readonly pageSize = signal(20);
  readonly pageIndex = signal(0);
  studyId: number | null = null;

  readonly filterForm = new FormGroup({
    search: new FormControl('', { nonNullable: true }),
    status: new FormControl<string | null>(null),
  });

  constructor(
    private readonly siteService: SiteService,
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
    this.siteService
      .list(this.studyId ?? undefined, search || undefined, this.pageIndex(), this.pageSize(), status)
      .subscribe((page) => {
        this.sites.set(page.content);
        this.totalElements.set(page.totalElements);
      });
  }

  onPage(event: PageEvent): void {
    this.pageIndex.set(event.pageIndex);
    this.pageSize.set(event.pageSize);
    this.load();
  }

  statusLabel(status: string): string {
    return status === 'PENDING_ACTIVATION' ? 'Pending Activation' : 'Active';
  }
}
