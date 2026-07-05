import { DatePipe, DecimalPipe, KeyValuePipe } from '@angular/common';
import { Component, OnInit, computed, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { MatAutocompleteModule } from '@angular/material/autocomplete';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { ChartConfiguration } from 'chart.js';
import { BaseChartDirective } from 'ng2-charts';
import { AuthService } from '../../core/auth/auth.service';
import { DashboardService, DashboardSummaryResponse } from '../../core/dashboard/dashboard.service';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatAutocompleteModule,
    BaseChartDirective,
    DatePipe,
    DecimalPipe,
    KeyValuePipe,
  ],
  templateUrl: './dashboard.component.html',
})
export class DashboardComponent implements OnInit {
  readonly summary = signal<DashboardSummaryResponse | null>(null);
  readonly errorMessage = signal<string | null>(null);
  readonly accessDenied = signal(false);

  readonly countryOptions = signal<string[]>([]);
  readonly phaseOptions = signal<string[]>([]);

  readonly filterForm = new FormGroup({
    studyId: new FormControl<number | null>(null),
    country: new FormControl('', { nonNullable: true }),
    siteId: new FormControl<number | null>(null),
    phase: new FormControl('', { nonNullable: true }),
  });

  readonly filteredCountryOptions = computed(() => {
    const typed = (this.countryControlValue() ?? '').toLowerCase();
    return this.countryOptions().filter((c) => c.toLowerCase().includes(typed));
  });

  readonly filteredPhaseOptions = computed(() => {
    const typed = (this.phaseControlValue() ?? '').toLowerCase();
    return this.phaseOptions().filter((p) => p.toLowerCase().includes(typed));
  });

  private readonly countryControlValue = signal('');
  private readonly phaseControlValue = signal('');

  readonly enrollmentChartData = computed<ChartConfiguration<'bar'>['data']>(() => {
    const s = this.summary();
    const entries = Object.entries(s?.enrollmentByStatus ?? {});
    return { labels: entries.map(([k]) => k), datasets: [{ label: 'Subjects', data: entries.map(([, v]) => v) }] };
  });

  readonly siteActivationChartData = computed<ChartConfiguration<'pie'>['data']>(() => {
    const s = this.summary();
    const entries = Object.entries(s?.siteActivationByStatus ?? {});
    return { labels: entries.map(([k]) => k), datasets: [{ data: entries.map(([, v]) => v) }] };
  });

  readonly barChartOptions: ChartConfiguration<'bar'>['options'] = { responsive: true };
  readonly pieChartOptions: ChartConfiguration<'pie'>['options'] = { responsive: true };

  constructor(
    readonly authService: AuthService,
    private readonly dashboardService: DashboardService,
  ) {}

  ngOnInit(): void {
    this.filterForm.controls.country.valueChanges.subscribe((v) => this.countryControlValue.set(v));
    this.filterForm.controls.phase.valueChanges.subscribe((v) => this.phaseControlValue.set(v));

    this.dashboardService.filterOptions().subscribe({
      next: (options) => {
        this.countryOptions.set(options.countries);
        this.phaseOptions.set(options.phases);
      },
      error: () => {
        // Non-fatal: the free-text filter inputs still work without autocomplete suggestions.
      },
    });

    this.load();
  }

  load(): void {
    const raw = this.filterForm.getRawValue();
    this.errorMessage.set(null);
    this.accessDenied.set(false);
    this.dashboardService
      .summary({ studyId: raw.studyId, country: raw.country || null, siteId: raw.siteId, phase: raw.phase || null })
      .subscribe({
        next: (s) => this.summary.set(s),
        error: (err) => {
          if (err.status === 403) {
            this.accessDenied.set(true);
          } else {
            this.errorMessage.set(err.error?.message ?? 'Could not load dashboard.');
          }
        },
      });
  }

  applyFilters(): void {
    this.load();
  }

  export(format: 'pdf' | 'excel'): void {
    const raw = this.filterForm.getRawValue();
    this.dashboardService
      .export({ studyId: raw.studyId, country: raw.country || null, siteId: raw.siteId, phase: raw.phase || null }, format)
      .subscribe((blob) => {
        const url = URL.createObjectURL(blob);
        const anchor = document.createElement('a');
        anchor.href = url;
        anchor.download = `dashboard-report.${format === 'excel' ? 'xlsx' : 'pdf'}`;
        anchor.click();
        URL.revokeObjectURL(url);
      });
  }
}
