import { Component, OnInit, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { HasRoleDirective } from '../../../core/auth/has-role.directive';
import { SiteResponse, SiteService } from '../../../core/sites/site.service';

@Component({
  selector: 'app-site-list',
  standalone: true,
  imports: [MatButtonModule, MatPaginatorModule, RouterLink, HasRoleDirective],
  templateUrl: './site-list.component.html',
})
export class SiteListComponent implements OnInit {
  readonly sites = signal<SiteResponse[]>([]);
  readonly totalElements = signal(0);
  readonly pageSize = signal(20);
  readonly pageIndex = signal(0);
  studyId: number | null = null;

  constructor(
    private readonly siteService: SiteService,
    private readonly route: ActivatedRoute,
  ) {}

  ngOnInit(): void {
    const studyIdParam = this.route.snapshot.queryParamMap.get('studyId');
    this.studyId = studyIdParam ? Number(studyIdParam) : null;
    this.load();
  }

  load(): void {
    this.siteService.list(this.studyId ?? undefined, undefined, this.pageIndex(), this.pageSize()).subscribe((page) => {
      this.sites.set(page.content);
      this.totalElements.set(page.totalElements);
    });
  }

  onPage(event: PageEvent): void {
    this.pageIndex.set(event.pageIndex);
    this.pageSize.set(event.pageSize);
    this.load();
  }
}
