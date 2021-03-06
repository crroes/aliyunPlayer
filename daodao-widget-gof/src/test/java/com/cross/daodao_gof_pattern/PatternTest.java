package com.cross.daodao_gof_pattern;

import com.cross.daodao_gof_pattern.adapter.ExecuteAdapter;
import com.cross.daodao_gof_pattern.iterator.Book;
import com.cross.daodao_gof_pattern.iterator.BookShelf;
import com.cross.daodao_gof_pattern.iterator.Iterator;

import org.junit.Test;

/**
 * Created by cross on 2018/7/30.
 * <p>描述:设计模式的单元测试
 */

public class PatternTest {

	@Test
	public void iterator(){
		BookShelf bookShelf = new BookShelf();
		bookShelf.appendBook(new Book("Around the World in 80 Days"));
		bookShelf.appendBook(new Book("Bible"));
		bookShelf.appendBook(new Book("Cinderella"));
		bookShelf.appendBook(new Book("Daddy Long Legs"));
		bookShelf.appendBook(new Book("Master branch"));
		bookShelf.appendBook(new Book("测试Git创建、合并分支"));
		bookShelf.appendBook(new Book("测试Git本地提交"));

		Iterator<Book> iterator = bookShelf.iterator();
		while (iterator.hasNext()) {
			Book next = iterator.next();
			System.out.println(next.getName());
		}
	}

	@Test
	public void adapter(){
		ExecuteAdapter.execute(ExecuteAdapter.Module.EXTEND);
//		ExecuteAdapter.execute(ExecuteAdapter.Module.DELEGATE);
	}
}
