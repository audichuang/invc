import { Component, Input } from '@angular/core';
import { TaskEvent } from '../../services/task.service';

@Component({
    selector: 'app-task-events',
    template: `
    <div class="card">
      <div class="card-header bg-success text-white">
        <h4>事件流</h4>
      </div>
      <div class="card-body">
        <div class="events-container" style="max-height: 400px; overflow-y: auto;">
          <div *ngIf="events.length === 0" class="text-center text-muted">
            尚未收到任何事件
          </div>
          
          <div *ngFor="let event of events" class="alert" 
               [ngClass]="{
                 'alert-info': event.status === 'CONNECTED' || event.status === 'PROCESSING',
                 'alert-success': event.status === 'COMPLETED' || event.status === 'SUBTASK_COMPLETED',
                 'alert-danger': event.status === 'FAILED' || event.status === 'ERROR'
               }">
            <div class="d-flex justify-content-between">
              <strong>{{ event.status }}</strong>
              <small>{{ event.correlationId }}</small>
            </div>
            <div>{{ event.message }}</div>
            <div *ngIf="event.result" class="mt-2">
              <strong>結果:</strong> {{ event.result }}
            </div>
            <div *ngIf="event.finalEvent" class="mt-2 badge bg-warning text-dark">
              最終事件
            </div>
          </div>
        </div>
      </div>
    </div>
  `,
    styles: []
})
export class TaskEventsComponent {
    @Input() events: TaskEvent[] = [];
} 