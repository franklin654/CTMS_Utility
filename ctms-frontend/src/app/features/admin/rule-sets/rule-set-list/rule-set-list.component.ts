import { Component, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { RuleSetService, RuleSetSummaryResponse } from '../../../../core/rule-sets/rule-set.service';

@Component({
  selector: 'app-rule-set-list',
  standalone: true,
  imports: [RouterLink, MatButtonModule],
  templateUrl: './rule-set-list.component.html',
})
export class RuleSetListComponent implements OnInit {
  readonly ruleSets = signal<RuleSetSummaryResponse[]>([]);
  readonly errorMessage = signal<string | null>(null);

  constructor(private readonly ruleSetService: RuleSetService) {}

  ngOnInit(): void {
    this.ruleSetService.list().subscribe({
      next: (sets) => this.ruleSets.set(sets),
      error: (err) => this.errorMessage.set(err.error?.message ?? 'Could not load rule sets.'),
    });
  }
}
