import { Component, OnInit, OnDestroy } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { TaskService, TaskRequest, TaskEvent } from '../../services/task.service';
import { Subscription } from 'rxjs';

@Component({
    selector: 'app-task-form',
    template: `
    <div class="row">
      <div class="col-md-6">
        <div class="card">
          <div class="card-header bg-info text-white">
            <h4>發起任務</h4>
          </div>
          <div class="card-body">
            <form [formGroup]="taskForm" (ngSubmit)="startTask()">
              <div class="mb-3">
                <label for="taskName" class="form-label">任務名稱</label>
                <input type="text" class="form-control" id="taskName" formControlName="taskName">
                <div *ngIf="taskForm.get('taskName')?.invalid && taskForm.get('taskName')?.touched" class="text-danger">
                  請輸入任務名稱
                </div>
              </div>
              
              <div class="mb-3">
                <label for="numberOfSubtasks" class="form-label">子任務數量</label>
                <input type="number" class="form-control" id="numberOfSubtasks" formControlName="numberOfSubtasks">
                <div *ngIf="taskForm.get('numberOfSubtasks')?.invalid && taskForm.get('numberOfSubtasks')?.touched" class="text-danger">
                  子任務數量必須介於 1-10 之間
                </div>
              </div>
              
              <button type="submit" class="btn btn-primary" [disabled]="taskForm.invalid || isLoading">
                {{ isLoading ? '處理中...' : '發起任務' }}
              </button>
            </form>
          </div>
        </div>
      </div>
      
      <div class="col-md-6">
        <app-task-events [events]="eventsList"></app-task-events>
      </div>
    </div>
  `,
    styles: []
})
export class TaskFormComponent implements OnInit, OnDestroy {
    taskForm: FormGroup;
    isLoading = false;
    eventsList: TaskEvent[] = [];
    currentCorrelationId: string = '';
    private subscription: Subscription = new Subscription();

    constructor(
        private fb: FormBuilder,
        private taskService: TaskService
    ) {
        this.taskForm = this.fb.group({
            taskName: ['', [Validators.required]],
            numberOfSubtasks: [3, [Validators.required, Validators.min(1), Validators.max(10)]]
        });
    }

    ngOnInit(): void {
        this.subscription.add(
            this.taskService.events$.subscribe(event => {
                console.log('收到事件:', event);
                this.eventsList = [...this.eventsList, event];

                if (event.finalEvent) {
                    this.isLoading = false;
                }
            })
        );
    }

    ngOnDestroy(): void {
        this.subscription.unsubscribe();
        this.taskService.disconnectEventStream();
    }

    startTask(): void {
        if (this.taskForm.valid) {
            this.isLoading = true;
            this.eventsList = [];

            // 產生新的 correlationId
            this.currentCorrelationId = this.taskService.generateCorrelationId();

            const request: TaskRequest = {
                correlationId: this.currentCorrelationId,
                taskName: this.taskForm.get('taskName')?.value,
                numberOfSubtasks: this.taskForm.get('numberOfSubtasks')?.value
            };

            console.log('發起任務請求:', request);

            // 建立 SSE 連接
            this.taskService.connectToEventStream(this.currentCorrelationId);

            // 發起任務請求
            this.taskService.initiateTask(request).subscribe({
                next: (response) => {
                    console.log('任務請求成功:', response);
                },
                error: (error) => {
                    console.error('任務請求失敗:', error);
                    this.isLoading = false;
                    this.eventsList.push({
                        correlationId: this.currentCorrelationId,
                        status: 'ERROR',
                        message: '任務請求失敗: ' + (error.message || JSON.stringify(error)),
                        finalEvent: true
                    });
                    this.taskService.disconnectEventStream();
                }
            });
        }
    }
} 