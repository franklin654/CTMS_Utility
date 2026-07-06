import { Pipe, PipeTransform } from '@angular/core';

/** Maps any status/severity/priority-like label used across the app to one of the
 * `.chip-*` classes in styles.css -- the app's one signature visual language for
 * state, applied consistently everywhere a Study/Site/Subject/Visit/Document/
 * Payment/AdverseEvent/Task status is shown, rather than each feature inventing
 * its own ad hoc badge coloring. */
const CHIP_CLASS: Record<string, string> = {
  // Lifecycle "active/current/good" states
  ACTIVE: 'chip-active',
  CONDUCT: 'chip-active',
  ENROLLED: 'chip-active',
  IN_TREATMENT: 'chip-active',
  COMPLETE: 'chip-active',
  COMPLETED: 'chip-active',
  CURRENT: 'chip-active',
  APPROVED: 'chip-active',
  RESOLVED: 'chip-active',
  RELEASED: 'chip-active',
  ON_TRACK: 'chip-active',
  REACHED: 'chip-active',

  // Pending / awaiting-action states
  DRAFT: 'chip-pending',
  PENDING: 'chip-pending',
  PENDING_ACTIVATION: 'chip-pending',
  PENDING_REVIEW: 'chip-pending',
  SCHEDULED: 'chip-pending',
  SCREENED: 'chip-pending',
  UNDER_REVIEW: 'chip-pending',
  SUBMITTED: 'chip-pending',
  HELD: 'chip-pending',
  ON_HOLD: 'chip-pending',
  OPEN: 'chip-pending',
  ABNORMAL: 'chip-pending',
  MEDIUM: 'chip-pending',
  MODERATE: 'chip-pending',
  MAJOR: 'chip-pending',

  // Critical / negative / terminal-bad states
  WITHDRAWN: 'chip-critical',
  REJECTED: 'chip-critical',
  MISSED: 'chip-critical',
  ESCALATED: 'chip-critical',
  DELAYED: 'chip-critical',
  OVERDUE: 'chip-critical',
  HIGH: 'chip-critical',
  SEVERE: 'chip-critical',
  LIFE_THREATENING: 'chip-critical',
  CRITICAL: 'chip-critical',
  ACCESS_DENIED: 'chip-critical',
  DELETE: 'chip-critical',

  // Audit-log actions -- quiet/informational by default
  CREATE: 'chip-active',
  APPROVE: 'chip-active',
  UPDATE: 'chip-info',
  TRANSITION: 'chip-info',

  // Quiet / archival / superseded states
  SUPERSEDED: 'chip-draft',
  ARCHIVED: 'chip-draft',
  CLOSEOUT: 'chip-draft',
  MINOR: 'chip-draft',
  MILD: 'chip-draft',
  LOW: 'chip-draft',

  // Informational / brand-neutral flags
  RESCHEDULED: 'chip-info',
  UNREAD: 'chip-info',
  'AD-HOC': 'chip-info',
  ADHOC: 'chip-info',
};

@Pipe({ name: 'statusChip', standalone: true })
export class StatusChipPipe implements PipeTransform {
  transform(value: string | null | undefined): string {
    if (!value) {
      return 'chip chip-draft';
    }
    const key = value.toUpperCase().replace(/\s+/g, '_');
    return `chip ${CHIP_CLASS[key] ?? 'chip-draft'}`;
  }
}
