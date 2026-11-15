export function waitForPreparation(signal, milliseconds=1200) {
  return new Promise((resolve,reject)=>{
    const abort=()=>{clearTimeout(timer);const error=new Error('Listing cancelled');error.name='AbortError';reject(error);};
    const timer=setTimeout(()=>{signal?.removeEventListener('abort',abort);resolve();},milliseconds);
    if(signal?.aborted)abort();else signal?.addEventListener('abort',abort,{once:true});
  });
}
