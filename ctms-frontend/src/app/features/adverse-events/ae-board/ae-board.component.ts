import { DatePipe } from '@angular/common';
import { Component, OnInit, computed, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AdverseEventResponse, AdverseEventService } from '../../../core/adverse-events/adverse-event.service';

const COLUMNS: { status: AdverseEventResponse['status']; label: string }[] = [
  { status: 'OPEN', label: 'Open' },
  { status: 'UNDER_REVIEW', label: 'Under Review' },
  { status: 'RESOLVED', label: 'Resolved' },
];

@Component({
  selector: 'app-ae-board',
  standalone: true,
  imports: [DatePipe, RouterLink],
  templateUrl: './ae-board.component.html',
})
export class AeBoardComponent implements OnInit {
  readonly columns = COLUMNS;
  readonly events = signal<AdverseEventResponse[]>([]);
  readonly errorMessage = signal<string | null>(null);

  readonly byStatus = computed(() => {
    const map = new Map<string, AdverseEventResponse[]>();
    for (const col of COLUMNS) {
      map.set(col.status, this.events().filter((e) => e.status === col.status));
    }
    return map;
  });

  constructor(private readonly adverseEventService: AdverseEventService) {}

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.adverseEventService.board().subscribe({
      next: (events) => this.events.set(events),
      error: (err) => this.errorMessage.set(err.error?.message ?? 'Could not load adverse events.'),
    });
  }

  severityClass(severity: string): string {
    if (severity === 'LIFE_THREATENING') {
      return 'bg-red-100 text-red-700';
    }
    if (severity === 'SEVERE') {
      return 'bg-orange-100 text-orange-700';
    }
    if (severity === 'MODERATE') {
      return 'bg-amber-100 text-amber-700';
    }
    return 'bg-gray-100 text-gray-600';
  }

  eventsFor(status: string): AdverseEventResponse[] {
    return this.byStatus().get(status) ?? [];
  }
}
