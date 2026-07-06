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

/** Maps status/category labels to the app's status-color language (see .chip-*
 * classes in styles.css) so dashboard charts read as an extension of that
 * system rather than defaulting to Chart.js's own generic palette. */
const STATUS_COLORS: Record<string, string> = {
  SCREENED: '#52606d', // graphite -- draft/neutral
  ENROLLED: '#1b4f72', // prussian -- brand/in-progress
  IN_TREATMENT: '#0e7c7b', // teal -- active
  COMPLETED: '#0e7c7b', // teal -- active/success
  WITHDRAWN: '#9b3a34', // brick -- critical
  PENDING_ACTIVATION: '#b7791f', // amber -- pending
  ACTIVE: '#0e7c7b', // teal -- active
};
const FALLBACK_COLOR = '#8a94a6';

function colorsFor(labels: string[]): string[] {
  return labels.map((label) => STATUS_COLORS[label] ?? FALLBACK_COLOR);
}

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
    const labels = entries.map(([k]) => k);
    return {
      labels,
      datasets: [{ label: 'Subjects', data: entries.map(([, v]) => v), backgroundColor: colorsFor(labels), borderRadius: 2 }],
    };
  });

  readonly siteActivationChartData = computed<ChartConfiguration<'pie'>['data']>(() => {
    const s = this.summary();
    const entries = Object.entries(s?.siteActivationByStatus ?? {});
    const labels = entries.map(([k]) => k);
    return { labels, datasets: [{ data: entries.map(([, v]) => v), backgroundColor: colorsFor(labels) }] };
  });

  readonly barChartOptions: ChartConfiguration<'bar'>['options'] = {
    responsive: true,
    plugins: { legend: { labels: { font: { family: 'IBM Plex Sans' } } } },
    scales: {
      x: { ticks: { font: { family: 'IBM Plex Mono', size: 10 } }, grid: { display: false } },
      y: { ticks: { font: { family: 'IBM Plex Sans' } } },
    },
  };
  readonly pieChartOptions: ChartConfiguration<'pie'>['options'] = {
    responsive: true,
    plugins: { legend: { labels: { font: { family: 'IBM Plex Sans' } } } },
  };

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
