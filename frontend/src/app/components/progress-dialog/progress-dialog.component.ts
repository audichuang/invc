import { Component, Inject, OnInit, OnDestroy } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { FundBondItem } from '../fund-bond-table/fund-bond-table.component';
import { TaskService, TaskEvent } from '../../services/task.service';
import { Subscription } from 'rxjs';

export interface ProcessStatus {
    id: string;
    name: string;
    type: 'fund' | 'bond';
    code: string;
    status: 'pending' | 'processing' | 'completed' | 'failed';
    message?: string;
}

export interface ProgressDialogData {
    correlationId: string;
    items: FundBondItem[];
}

@Component({
    selector: 'app-progress-dialog',
    templateUrl: './progress-dialog.component.html',
    styleUrls: ['./progress-dialog.component.css']
})
export class ProgressDialogComponent implements OnInit, OnDestroy {
    displayedColumns: string[] = ['name', 'type', 'code', 'status'];
    itemStatuses: ProcessStatus[] = [];

    totalItems = 0;
    completedItems = 0;
    failedItems = 0;

    fundCorrelationId: string;
    bondCorrelationId: string;

    private eventsSubscription: Subscription = new Subscription();

    constructor(
        private dialogRef: MatDialogRef<ProgressDialogComponent>,
        private taskService: TaskService,
        @Inject(MAT_DIALOG_DATA) public data: ProgressDialogData
    ) {
        this.fundCorrelationId = data.correlationId + '-fund';
        this.bondCorrelationId = data.correlationId + '-bond';
    }

    ngOnInit(): void {
        // 初始化項目狀態
        this.itemStatuses = this.data.items.map(item => ({
            ...item,
            status: 'pending',
            message: '等待處理'
        }));

        this.totalItems = this.itemStatuses.length;

        // 訂閱事件更新
        this.eventsSubscription = this.taskService.events$.subscribe(event => {
            this.handleTaskEvent(event);
        });
    }

    ngOnDestroy(): void {
        if (this.eventsSubscription) {
            this.eventsSubscription.unsubscribe();
        }
    }

    handleTaskEvent(event: TaskEvent): void {
        console.log('收到事件:', event);

        // 判斷事件來源（基金或債券）
        const eventType = event.system || (event.correlationId.includes('-fund') ? 'fund' : 'bond');

        // 找出屬於該系統類型的項目
        const typeItems = this.itemStatuses.filter(item => item.type === eventType);

        // 通用事件處理
        if (event.status === 'PROCESSING' || event.status === 'CONNECTED') {
            // 處理中
            typeItems.forEach(item => {
                if (item.status === 'pending') {
                    item.status = 'processing';
                    item.message = '處理中...';
                }
            });
        } else if (event.status === 'SUBTASK_COMPLETED') {
            // 子任務完成，嘗試更新項目狀態
            // 處理子任務索引
            const subtaskIndex = this.extractSubtaskIndex(event.message, event.result);

            if (subtaskIndex !== null && subtaskIndex >= 0 && subtaskIndex < typeItems.length) {
                const item = typeItems[subtaskIndex];
                if (item) {
                    item.status = 'completed';
                    item.message = '處理完成';
                    this.completedItems++;
                }
            }
        } else if (event.status === 'COMPLETED') {
            // 所有任務完成 - 將所有未完成的項目標記為完成
            typeItems
                .filter(item => item.status === 'processing' || item.status === 'pending')
                .forEach(item => {
                    item.status = 'completed';
                    item.message = '處理完成';
                    this.completedItems++;
                });

            // 檢查是否可以關閉對話框
            const allTasksCompleted = this.itemStatuses.every(
                item => item.status === 'completed' || item.status === 'failed'
            );

            if (allTasksCompleted) {
                this.dialogRef.disableClose = false;
            }
        } else if (event.status === 'FAILED' || event.status === 'ERROR') {
            // 處理失敗
            typeItems
                .filter(item => item.status !== 'completed')
                .forEach(item => {
                    item.status = 'failed';
                    item.message = event.message || '處理失敗';
                    this.failedItems++;
                });
        }
    }

    getStatusClass(status: string): string {
        switch (status) {
            case 'completed': return 'status-completed';
            case 'failed': return 'status-failed';
            case 'processing': return 'status-processing';
            default: return 'status-pending';
        }
    }

    getProgressPercentage(): number {
        if (this.totalItems === 0) return 0;
        return Math.round(((this.completedItems + this.failedItems) / this.totalItems) * 100);
    }

    allTasksCompleted(): boolean {
        return this.itemStatuses.every(
            item => item.status === 'completed' || item.status === 'failed'
        );
    }

    closeDialog(): void {
        const allTasksCompleted = this.allTasksCompleted();

        if (allTasksCompleted) {
            this.dialogRef.close();
        }
    }

    private extractSubtaskIndex(message: string, result: any): number | null {
        console.log('嘗試解析子任務索引:', message, result);

        // 嘗試從消息中解析子任務索引
        if (typeof message === 'string') {
            // 匹配 "債券子任務 0 已完成" 或 "子任務 0 的結果" 格式
            const bondMatch = message.match(/債券子任務\s+(\d+)\s+已完成/);
            const fundMatch = message.match(/子任務\s+(\d+)/);

            if (bondMatch && bondMatch[1]) {
                return parseInt(bondMatch[1], 10);
            }

            if (fundMatch && fundMatch[1]) {
                return parseInt(fundMatch[1], 10);
            }
        }

        // 嘗試從結果中解析子任務索引
        if (typeof result === 'string') {
            const resultMatch = result.match(/子任務\s+(\d+)\s+的結果/);
            if (resultMatch && resultMatch[1]) {
                return parseInt(resultMatch[1], 10);
            }
        }

        return null;
    }
} 