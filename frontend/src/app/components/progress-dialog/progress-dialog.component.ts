import { Component, Inject, OnInit, OnDestroy, ChangeDetectorRef, ViewEncapsulation } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { FundBondItem } from '../fund-bond-table/fund-bond-table.component';
import { TaskService, TaskEvent } from '../../services/task.service';
import { Subscription } from 'rxjs';
import { trigger, transition, style, animate } from '@angular/animations';

export interface ProcessStatus {
    id: string;
    name: string;
    type: 'fund' | 'bond';
    code: string;
    status: 'pending' | 'processing' | 'completed' | 'failed';
    message?: string;
    originalIndex?: number; // 保存原始索引
    progress: number; // 進度百分比 (0-100)
    detailedStatus: string; // 更詳細的中文狀態描述
    totalSubtasks: number; // 總子任務數
    completedSubtasksCount: number; // 已完成子任務數
}

export interface ProgressDialogData {
    correlationId: string;
    items: FundBondItem[];
}

@Component({
    selector: 'app-progress-dialog',
    templateUrl: './progress-dialog.component.html',
    styleUrls: ['./progress-dialog.component.css'],
    encapsulation: ViewEncapsulation.None,
    animations: [
        trigger('fadeInOut', [
            transition(':enter', [
                style({ opacity: 0 }),
                animate('300ms ease-out', style({ opacity: 1 }))
            ]),
            transition(':leave', [
                animate('300ms ease-in', style({ opacity: 0 }))
            ])
        ])
    ]
})
export class ProgressDialogComponent implements OnInit, OnDestroy {

    displayedColumns: string[] = ['name', 'type', 'code', 'detailedStatus', 'progress'];
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
            originalIndex: index,
            progress: 0, // 初始化進度
            detailedStatus: '等待中',
            totalSubtasks: 0, // 初始化
            completedSubtasksCount: 0 // 初始化
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
            // 可以考慮為所有此類型的 pending 項目更新 detailedStatus
            affectedItems.forEach(item => {
                if (item.status === 'pending') {
                    item.detailedStatus = '已連接，等待任務開始';
                }
            });
        }
        // 處理通用錯誤事件
        else if (event.status === 'ERROR' || event.status === 'FAILED' || event.status === 'STREAM_CLOSED') {
            let newFailed = 0;
            affectedItems
                .filter(item => item.status !== 'completed' && item.status !== 'failed')
                .forEach(item => {
                    item.status = 'failed';
                    item.detailedStatus = event.message || `${eventType}系統錯誤或連線中斷`;
                    item.progress = item.progress > 0 ? item.progress : 0; // 如果已經開始，保持進度；否則為0。或統一設為100表示結束
                    // item.progress = 100; // 或者將失敗的任務進度也視為100% (表示處理結束)
                    newFailed++;
                });

            this.failedItems += newFailed;
            this.checkAllCompleted();
        }
    }

    // 更新單個項目的狀態
    private updateItemStatus(item: ProcessStatus, event: TaskEvent): void {
        if (event.status === 'PROCESSING') {
            if (item.status === 'pending' || item.status !== 'processing') {
                item.status = 'processing';
                item.detailedStatus = '開始處理...'; // 更新初始狀態文字
                item.totalSubtasks = (item.type === 'fund') ? 5 : 3; // 根據類型設定總子任務數
                item.completedSubtasksCount = 0;
                item.progress = item.totalSubtasks > 0 ? 5 : 10; // 如果有子任務，初始進度小一點，否則直接給10%
            }
        } else if (event.status === 'SUBTASK_COMPLETED') {
            if (item.status === 'processing') {
                item.detailedStatus = event.message || `子任務進展...`;
                if (item.totalSubtasks > 0) {
                    item.completedSubtasksCount++;
                    item.progress = Math.min(100, Math.round((item.completedSubtasksCount / item.totalSubtasks) * 100));
                } else {
                    // 沒有設定 totalSubtasks，做一個估算增加
                    if (item.progress < 90) {
                        item.progress += 15; // 每次子任務完成增加一個估算值
                    }
                }
            }
        } else if (event.status === 'COMPLETED') {
            if (item.status !== 'completed') {
                item.status = 'completed';
                item.detailedStatus = event.message || '處理完成';
                item.progress = 100;
                this.completedItems++;
                this.checkAllCompleted();
            }
        } else if (event.status === 'FAILED' || event.status === 'ERROR') {
            if (item.status !== 'failed') {
                item.status = 'failed';
                item.detailedStatus = event.message || '處理失敗';
                // 失敗時，進度可以保持原樣，或設為100表示流程結束，或設為0
                // 這裡我們選擇保持失敗前的進度，如果之前有進度的話
                item.progress = item.progress > 0 ? item.progress : 0;
                this.failedItems++;
                this.checkAllCompleted();
            }
        } else if (event.status === 'ALL_TASKS_COMPLETED') {
            // 這個事件是針對整個 SSE 連線的，目前 item-specific 處理中可能不會直接用到
            // 但如果邏輯需要，可以處理
            console.log('收到 ALL_TASKS_COMPLETED 事件，更新對應項目群組的最終狀態（如果需要）');
        }

        // 其他可能的狀態，例如 STREAM_CLOSED，已在通用事件處理中覆蓋
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