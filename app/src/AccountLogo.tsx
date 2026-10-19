import {BrandMark} from './BrandMark';
import {useSocial} from './Social';
export type PublicLogo={id:string;name:string;resource:string|null};
export function PlayerLogo({logo,className='logo'}:{logo?:PublicLogo|null;className?:string}){
 const source=logo?['unbox-creator-crown','unbox-fluttering-cash'].includes(logo.id)?`/assets/logos/${logo.id}.svg`:logo.resource?.startsWith('https://')?logo.resource:null:null;
 return source?<img className={className} src={source} alt={logo?.name||''}/>:<BrandMark className={className} decorative/>;
}
export function AccountLogo({microsoft,className='logo'}:{microsoft:boolean;className?:string}){
 const {view}=useSocial(),assets=view.assets,selected=assets?.equipment.find(e=>e.slot==='logo')?.item,item=assets?.items.find(i=>i.id===selected&&i.kind==='logo');
 return <PlayerLogo logo={microsoft?item:null} className={className}/>;
}
