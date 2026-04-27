import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { AuthService, UserInfo } from '../../core/services/auth.service';

interface Item {
  id: number;
  name: string;
  description: string;
}

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss'
})
export class DashboardComponent implements OnInit {
  private readonly http = inject(HttpClient);
  readonly authService = inject(AuthService);

  user: UserInfo | null = null;
  items: Item[] = [];
  loading = true;
  error: string | null = null;

  ngOnInit(): void {
    this.authService.getUserInfo().subscribe(user => (this.user = user));
    this.loadItems();
  }

  loadItems(): void {
    this.loading = true;
    this.http.get<Item[]>('/bff/api/items').subscribe({
      next: items => {
        this.items = items;
        this.loading = false;
      },
      error: () => {
        this.error = 'Impossible de charger les données.';
        this.loading = false;
      }
    });
  }

  logout(): void {
    this.authService.logout();
  }
}
