/** Serializes native log transitions so route changes and rapid taps cannot create duplicate logs. */
export class RecordingLifecycle {
  active=false;
  private queue:Promise<void>=Promise.resolve();
  constructor(private start:()=>Promise<void>,private stop:()=>Promise<void>) {}
  adopt(active:boolean) { this.active=active; }
  settled(){return this.queue;}
  /** Only call after native confirms no file is actively recording. */
  async recover(preserve:(error:unknown)=>Promise<void>):Promise<boolean> {
    try {await this.set(false);return true;}
    catch(error){await preserve(error);this.active=false;return false;}
  }
  set(desired:boolean):Promise<void> {
    const operation=this.queue.then(async()=>{
      if(this.active===desired)return;
      if(desired)await this.start();else await this.stop();
      this.active=desired;
    });
    this.queue=operation.catch(()=>{});
    return operation;
  }
}

