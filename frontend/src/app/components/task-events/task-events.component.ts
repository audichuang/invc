import { Component, Input, ViewChild, ElementRef, AfterViewChecked, ChangeDetectionStrategy, OnChanges, SimpleChanges } from '@angular/core';
import { TaskEvent } from '../../services/task.service';

@Component({
  selector: 'app-task-events',
  templateUrl: './task-events.component.html',
  styleUrls: ['./task-events.component.css'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class TaskEventsComponent implements AfterViewChecked, OnChanges {
  @Input() events: TaskEvent[] = [];
  @ViewChild('eventsContainer') private eventsContainer!: ElementRef;

  private shouldScrollToBottom = false;
  private lastEventCount = 0;

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['events'] && changes['events'].currentValue.length > this.lastEventCount) {
      this.shouldScrollToBottom = true;
      this.lastEventCount = changes['events'].currentValue.length;
    }
    if (changes['events'] && changes['events'].currentValue.length === 0) {
      this.lastEventCount = 0;
    }
  }

  ngAfterViewChecked(): void {
    if (this.shouldScrollToBottom && this.eventsContainer) {
      this.scrollToBottom();
      this.shouldScrollToBottom = false;
    }
  }

  private scrollToBottom(): void {
    try {
      const element = this.eventsContainer.nativeElement;
      element.scrollTop = element.scrollHeight;
    } catch (err) {
      console.error("無法滾動事件容器:", err);
    }
  }

  trackByEventIdentity(index: number, event: TaskEvent): string {
    return `${event.status}-${event.receivedAt?.getTime()}-${index}`;
  }
} 