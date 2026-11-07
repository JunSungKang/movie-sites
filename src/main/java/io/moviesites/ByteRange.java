package io.moviesites;

public record ByteRange(long start,long end,boolean partial) {
    public long length(){return end<start?0:end-start+1;}
    public static ByteRange parse(String header,long size){
        if(header==null||header.isBlank())return new ByteRange(0,size-1,false);
        if(size<=0 || !header.matches("bytes=(\\d+-\\d*|-\\d+)"))throw new IllegalArgumentException("Invalid byte range");
        try{
            String[] parts=header.substring(6).split("-",-1);long start,end;
            if(parts[0].isEmpty()){long suffix=Long.parseLong(parts[1]);if(suffix<=0)throw new IllegalArgumentException();start=Math.max(0,size-suffix);end=size-1;}
            else{start=Long.parseLong(parts[0]);end=parts[1].isEmpty()?size-1:Math.min(Long.parseLong(parts[1]),size-1);}
            if(start>=size||start<0||end<start)throw new IllegalArgumentException();
            return new ByteRange(start,end,true);
        }catch(NumberFormatException e){throw new IllegalArgumentException("Invalid byte range");}
    }
}
