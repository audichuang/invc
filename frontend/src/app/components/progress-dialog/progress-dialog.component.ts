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
        // 判斷事件來源（基金或債券）
        const eventType = event.correlationId.includes('-fund') ? 'fund' : 'bond';

        // 通用事件處理
        if (event.status === 'PROCESSING') {
            // 處理中
            this.itemStatuses
                .filter(item => item.type === eventType)
                .forEach(item => {
                    item.status = 'processing';
                    item.message = '處理中...';
                });
        } else if (event.status === 'SUBTASK_COMPLETED') {
            // 子任務完成，更新特定項目狀態
            if (event.result) {
                // 假設result包含項目ID
                const itemId = this.extractItemId(event.result);
                const item = this.itemStatuses.find(i => i.id === itemId && i.type === eventType);

                if (item) {
                    item.status = 'completed';
                    item.message = '處理完成';
                    this.completedItems++;
                }
            }
        } else if (event.status === 'FAILED' || event.status === 'ERROR') {
            // 處理失敗
            if (event.result) {
                // 假設result包含項目ID
                const itemId = this.extractItemId(event.result);
                const item = this.itemStatuses.find(i => i.id === itemId && i.type === eventType);

                if (item) {
                    item.status = 'failed';
                    item.message = event.message || '處理失敗';
                    this.failedItems++;
                }
            } else {
                // 整體處理失敗
                this.itemStatuses
                    .filter(item => item.type === eventType && item.status === 'pending')
                    .forEach(item => {
                        item.status = 'failed';
                        item.message = event.message || '處理失敗';
                        this.failedItems++;
                    });
            }
        } else if (event.status === 'COMPLETED') {
            // 所有任務完成，檢查是否可以關閉對話框
            const allTasksCompleted = this.itemStatuses.every(
                item => item.status === 'completed' || item.status === 'failed'
            );

            if (allTasksCompleted) {
                this.dialogRef.disableClose = false;
            }
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

    private extractItemId(result: any): string {
        // 根據實際API返回格式調整
        if (typeof result === 'string' && result.includes('子任務')) {
            // 範例: '子任務 1 的結果'，提取數字1作為ID
            const match = result.match(/子任務\s+(\d+)\s+的結果/);
            return match ? match[1] : '';
        }
        return '';
    }
} 