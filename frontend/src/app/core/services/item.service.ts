import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface Item {
  id: number;
  name: string;
  description: string;
}

export interface ItemRequest {
  name: string;
  description?: string;
}

@Injectable({ providedIn: 'root' })
export class ItemService {
  private readonly http = inject(HttpClient);

  getAll(): Observable<Item[]> {
    return this.http.get<Item[]>('/bff/api/items');
  }

  create(payload: ItemRequest): Observable<Item> {
    return this.http.post<Item>('/bff/api/items', payload);
  }

  update(id: number, payload: ItemRequest): Observable<Item> {
    return this.http.put<Item>(`/bff/api/items/${id}`, payload);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`/bff/api/items/${id}`);
  }
}
