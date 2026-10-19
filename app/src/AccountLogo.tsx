import {BrandMark} from './BrandMark';
import {useSocial} from './Social';
export function AccountLogo({microsoft,className='logo'}:{microsoft:boolean;className?:string}){
 const {view}=useSocial(),assets=view.assets,selected=assets?.equipment.find(e=>e.slot==='logo')?.item,item=assets?.items.find(i=>i.id===selected&&i.kind==='logo');
 const source=microsoft&&item?['unbox-creator-crown','unbox-fluttering-cash'].includes(item.id)?`/assets/logos/${item.id}.svg`:item.resource?.startsWith('https://')?item.resource:null:null;
 return source?<img className={className} src={source} alt=""/>:<BrandMark className={className} decorative/>;
}
