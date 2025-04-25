import { Component, Inject, OnInit, OnDestroy, ChangeDetectorRef } from '@angular/core';
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
    originalIndex?: number; // 用於保存原始索引
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
        private cdr: ChangeDetectorRef,
        @Inject(MAT_DIALOG_DATA) public data: ProgressDialogData
    ) {
        this.fundCorrelationId = data.correlationId + '-fund';
        this.bondCorrelationId = data.correlationId + '-bond';
    }

    ngOnInit(): void {
        // 初始化項目狀態並添加索引信息
        this.itemStatuses = this.data.items.map((item, index) => ({
            ...item,
            status: 'pending',
            message: '等待處理',
            originalIndex: index
        }));

        this.totalItems = this.itemStatuses.length;

        // 訂閱事件更新
        this.eventsSubscription = this.taskService.events$.subscribe(event => {
            this.handleTaskEvent(event);
            this.cdr.detectChanges(); // 確保 UI 更新
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

        // 檢查是否這是特定項目的事件還是一般事件
        // 從correlationId中提取項目索引，例如: "xyz-fund-0" 中的0
        const itemIndexMatch = event.correlationId.match(/-(fund|bond)-(\d+)$/);

        if (itemIndexMatch && itemIndexMatch[2]) {
            // 這是針對特定項目的事件
            const type = itemIndexMatch[1] as 'fund' | 'bond'; // 'fund' 或 'bond'
            const itemIndex = parseInt(itemIndexMatch[2]);

            // 找出屬於該類型的所有項目
            const typeItems = this.itemStatuses.filter(item => item.type === type);

            // 確保索引在有效範圍內
            if (itemIndex >= 0 && itemIndex < typeItems.length) {
                const item = typeItems[itemIndex];

                // 根據事件狀態更新項目
                this.updateItemStatus(item, event);
            }
            return;
        }

        // 這是整體事件（或無法識別的項目事件）
        // 找出受影響的所有項目
        const affectedItems = this.itemStatuses.filter(item =>
            item.type === eventType
        );

        if (affectedItems.length === 0) return;

        // 處理整體連接事件
        if (event.status === 'CONNECTED') {
            console.log(`${eventType}系統已連接`);
        }
        // 處理通用錯誤事件
        else if (event.status === 'ERROR' || event.status === 'FAILED') {
            // 將所有待處理的項目設置為失敗
            let newFailed = 0;
            affectedItems
                .filter(item => item.status !== 'completed' && item.status !== 'failed')
                .forEach(item => {
                    item.status = 'failed';
                    item.message = event.message || `${eventType}系統錯誤`;
                    newFailed++;
                });

            this.failedItems += newFailed;
            this.checkAllCompleted();
        }
    }

    // 更新單個項目的狀態
    private updateItemStatus(item: ProcessStatus, event: TaskEvent): void {
        if (event.status === 'PROCESSING' || event.status === 'CONNECTED') {
            if (item.status === 'pending') {
                item.status = 'processing';
                item.message = '處理中...';
            }
        } else if (event.status === 'COMPLETED') {
            if (item.status !== 'completed') {
                item.status = 'completed';
                item.message = event.message || '處理完成';
                this.completedItems++;
                this.checkAllCompleted();
            }
        } else if (event.status === 'FAILED' || event.status === 'ERROR') {
            if (item.status !== 'failed') {
                item.status = 'failed';
                item.message = event.message || '處理失敗';
                this.failedItems++;
                this.checkAllCompleted();
            }
        }
    }

    // 檢查是否所有任務都已完成或失敗
    private checkAllCompleted(): void {
        const allTasksCompleted = this.allTasksCompleted();

        if (allTasksCompleted) {
            this.dialogRef.disableClose = false;
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
} 