/** Converts a mat-datepicker's Date value back to the plain ISO 'YYYY-MM-DD' string the backend
 * expects (LocalDate on the Java side) -- formatted from local date parts, not toISOString(),
 * to avoid UTC off-by-one-day shifts. */
export function toIsoDate(date: Date | null | undefined): string | null {
  if (!date) {
    return null;
  }
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

/** Parses a 'YYYY-MM-DD' string (as returned by the backend) into a local Date for mat-datepicker
 * to display -- avoids the timezone shift `new Date('YYYY-MM-DD')` introduces (that form is
 * parsed as UTC midnight, which can render as the previous day in negative-UTC-offset zones). */
export function fromIsoDate(value: string | null | undefined): Date | null {
  if (!value) {
    return null;
  }
  const [year, month, day] = value.split('-').map(Number);
  return new Date(year, month - 1, day);
}
