export type Difficulty = 'EASY' | 'MEDIUM' | 'HARD';

export interface BookFilter {
  readonly key: string;
  readonly label: string;
  readonly type: 'select';
  readonly options: readonly string[];
}
export type SectionType = 'BEGIN' | 'NODE' | 'END';
export type ConsequenceType = 'GAIN_HEALTH' | 'LOSE_HEALTH';

export interface BookQuery {
  readonly search?: string;
  readonly page?: number;
  readonly size?: number;
}

export interface BookSummary {
  readonly id: number;
  readonly title: string;
  readonly author: string;
  readonly difficulty: Difficulty;
}

export interface BookPage {
  readonly content: readonly BookSummary[];
  readonly page: number;
  readonly size: number;
  readonly totalElements: number;
  readonly totalPages: number;
  readonly last: boolean;
}

export interface BookDetails extends BookSummary {
  readonly beginningSectionId: number;
}

export interface Consequence {
  readonly type: ConsequenceType;
  readonly value: number;
  readonly text: string;
}

export interface BookChoice {
  readonly description: string;
  readonly gotoId: number;
  readonly consequence: Consequence | null;
}

export interface BookSection {
  readonly id: number;
  readonly text: string;
  readonly type: SectionType;
  readonly options: readonly BookChoice[];
}
